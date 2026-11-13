#!/usr/bin/env python3
"""
builds app/src/main/assets/blocks.bin from nanpa's "thousands block assignment, all regions" file.

  download: https://reports.nanpa.com/public/ThousandsBlockAssignment_All.zip
  unzip it into data-sources/, then run:  python3 tools/buildBlockData.py

file layout (big endian), read by BlockDirectory.parse:
  "NXB1", int asOf(yyyymmdd, the date of the source file)
  ushort companyCount, then companyCount x (utf ocn, utf name)
  int codeCount, then codeCount x (int key, int offset)   key = npa*1000 + nxx, offset = first entry of that code
  int entryCount, then entryCount x (ushort word, ushort assignMonths)
  a code with 10 entries has one per block digit 0-9; a code with 1 entry means all ten blocks are identical
  word: bits 14-15 status (0 assigned, 1 retained, 2 available, 3 available but has working numbers),
        bits 0-13 company index; 0xFFFF = no data for that block
  assignMonths = (year-1900)*12 + month, 0 when unknown
"""
import csv
import datetime
import pathlib
import re
import struct
import sys

source = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "data-sources/ThousandsBlockAssignment_All.txt")
target = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/blocks.bin")

MISSING = (0xFFFF, 0)


def ascii_text(value: str) -> str:
    return value.strip().encode("ascii", "replace").decode("ascii")


companies = {}
codes = {}
with open(source, encoding="latin-1", newline="") as handle:
    for row in csv.DictReader(handle):
        npa, nxx, x = row["NPA"].strip(), row["NXX"].strip(), row["X"].strip()
        if not (len(npa) == 3 and len(nxx) == 3 and x.isdigit() and npa.isdigit() and nxx.isdigit()):
            continue
        status = row["STATUS"].strip()
        months = 0
        if status in ("AS", "RT"):
            code = 0 if status == "AS" else 1
            index = companies.setdefault((ascii_text(row["OCN"]), ascii_text(row["Assigned To"])), len(companies))
            date = re.match(r"(\d+)/(\d+)/(\d{4})", row["Date Assigned"])
            if date:
                months = (int(date.group(3)) - 1900) * 12 + int(date.group(1))
        else:
            code = 3 if row["Contaminated (Y or N)"].strip() == "Y" else 2
            index = 0
        codes.setdefault(int(npa) * 1000 + int(nxx), [MISSING] * 10)[int(x)] = ((code << 14) | index, months)

assert len(companies) < 16384, "company index no longer fits in 14 bits"

entries = []
index_rows = []
for key in sorted(codes):
    blocks = codes[key]
    index_rows.append((key, len(entries)))
    if MISSING not in blocks and len(set(blocks)) == 1:
        entries.append(blocks[0])
    else:
        entries.extend(blocks)

as_of_date = datetime.date.fromtimestamp(source.stat().st_mtime)
as_of = as_of_date.year * 10000 + as_of_date.month * 100 + as_of_date.day

target.parent.mkdir(parents=True, exist_ok=True)
with open(target, "wb") as out:
    out.write(b"NXB1")
    out.write(struct.pack(">i", as_of))
    out.write(struct.pack(">H", len(companies)))
    for ocn, name in companies:
        for text in (ocn, name):
            data = text.encode("ascii")
            out.write(struct.pack(">H", len(data)) + data)
    out.write(struct.pack(">i", len(index_rows)))
    for key, offset in index_rows:
        out.write(struct.pack(">ii", key, offset))
    out.write(struct.pack(">i", len(entries)))
    for word, months in entries:
        out.write(struct.pack(">HH", word, months))

print(f"{len(index_rows)} codes, {len(entries)} entries, {len(companies)} carriers, as of {as_of} -> {target} ({target.stat().st_size // 1024} KB)")
