#!/usr/bin/env bash
# downloads the latest nanpa reports and rebuilds nanpa.bin and blocks.bin from them.
#
#   tools/refreshCarrierData.sh            refresh the data files only
#   tools/refreshCarrierData.sh --install  also rebuild and install the app on the connected phone
#
# nothing in app/src/main/assets is touched unless the download, the unzip and the build all succeed.
set -euo pipefail
cd "$(dirname "$0")/.."

base="https://reports.nanpa.com/public"
files=("CoCodeAssignment_Utilized_AllStates_Public" "ThousandsBlockAssignment_All")

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

for name in "${files[@]}"; do
  echo "downloading $name.zip ..."
  curl --fail --location --retry 3 --silent --show-error -o "$work/$name.zip" "$base/$name.zip"
  unzip -tq "$work/$name.zip" > /dev/null || { echo "$name.zip is not a valid zip, so nothing was changed"; exit 1; }
  unzip -qo "$work/$name.zip" -d "$work"
  [ -s "$work/$name.txt" ] || { echo "$name.txt wasn't in the zip, so nothing was changed"; exit 1; }
done

echo "building the data files ..."
python3 tools/buildNanpaData.py "$work/CoCodeAssignment_Utilized_AllStates_Public.txt" "$work/nanpa.bin"
python3 tools/buildBlockData.py "$work/ThousandsBlockAssignment_All.txt" "$work/blocks.bin"

mkdir -p app/src/main/assets data-sources
cp "$work/nanpa.bin" "$work/blocks.bin" app/src/main/assets/
# keep the raw reports too, the same place the manual download used
cp "$work"/CoCodeAssignment_Utilized_AllStates_Public.txt "$work"/ThousandsBlockAssignment_All.txt data-sources/
echo "done: app/src/main/assets/nanpa.bin and blocks.bin are up to date"

if [ "${1:-}" = "--install" ]; then
  ./gradlew testDebugUnitTest installDebug
fi
