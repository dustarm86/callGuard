#!/usr/bin/env python3
"""
builds app/src/main/assets/nanpa.bin from the nanpa "central office code assignment, utilized, all states" text file.

  download: https://reports.nanpa.com/public/CoCodeAssignment_Utilized_AllStates_Public.zip
  unzip it into data-sources/, then run:  python3 tools/buildNanpaData.py

file layout (big endian), read by NxxDirectory.parse:
  "NXX1", int asOf(yyyymmdd)
  ushort stateCount, then stateCount x 2 ascii bytes
  ushort companyCount, then companyCount x (utf ocn, utf name)
  int rateCenterCount, then rateCenterCount x utf name
  int recordCount, then recordCount x (int key, ushort company, ushort rateCenter, ubyte state, ubyte flags, ushort assignMonths)
  key = npa*1000 + nxx; flags: 1 pooled, 2 in service, 4 unavailable; assignMonths = (year-1900)*12 + month, 0 when unknown
"""
import csv
import pathlib
import re
import struct
import sys

source = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "data-sources/CoCodeAssignment_Utilized_AllStates_Public.txt")
target = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/nanpa.bin")


def ascii_text(value: str) -> str:
    return value.strip().encode("ascii", "replace").decode("ascii")


states, companies, rate_centers = {}, {}, {}
records = []
as_of = 0

with open(source, encoding="utf-8", errors="replace", newline="") as handle:
    reader = csv.reader(handle, delimiter="\t", quotechar='"')
    header = next(reader)
    match = re.search(r"(\d+)/(\d+)/(\d{4})", header[-1])
    if match:
        as_of = int(match.group(3)) * 10000 + int(match.group(1)) * 100 + int(match.group(2))
    for row in reader:
        if len(row) < 11:
            continue
        state, npanxx, ocn, company, rate_center, _effective, use, assigned, _ig, pooled, in_service = (c.strip() for c in row[:11])
        digits = npanxx.replace("-", "")
        if len(digits) != 6 or not digits.isdigit():
            continue
        state_id = states.setdefault(state[:2], len(states))
        company_id = companies.setdefault((ascii_text(ocn), ascii_text(company)), len(companies))
        rate_center_id = rate_centers.setdefault(ascii_text(rate_center), len(rate_centers))
        flags = (1 if pooled == "Y" else 0) | (2 if in_service == "Yes" else 0) | (4 if use == "UA" else 0)
        months = 0
        date = re.match(r"(\d+)/(\d+)/(\d{4})", assigned)
        if date:
            months = (int(date.group(3)) - 1900) * 12 + int(date.group(1))
        records.append((int(digits), company_id, rate_center_id, state_id, flags, months))

records.sort()
assert len(states) < 256 and len(companies) < 65536 and len(rate_centers) < 65536

target.parent.mkdir(parents=True, exist_ok=True)
with open(target, "wb") as out:
    out.write(b"NXX1")
    out.write(struct.pack(">i", as_of))
    out.write(struct.pack(">H", len(states)))
    for state in states:
        out.write(state.ljust(2)[:2].encode("ascii"))
    out.write(struct.pack(">H", len(companies)))
    for ocn, name in companies:
        for text in (ocn, name):
            data = text.encode("ascii")
            out.write(struct.pack(">H", len(data)) + data)
    out.write(struct.pack(">i", len(rate_centers)))
    for name in rate_centers:
        data = name.encode("ascii")
        out.write(struct.pack(">H", len(data)) + data)
    out.write(struct.pack(">i", len(records)))
    for record in records:
        out.write(struct.pack(">iHHBBH", *record))

print(f"{len(records)} codes, {len(companies)} carriers, {len(rate_centers)} rate centers, as of {as_of} -> {target} ({target.stat().st_size // 1024} KB)")
