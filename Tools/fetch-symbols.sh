#!/usr/bin/env bash
# Regenerates Android icon assets: one <sf-name>.symbolset per line of Tools/symbols.txt,
# containing the Material Symbols (Apache-2.0) Xcode-format template for that glyph.
set -euo pipefail
cd "$(dirname "$0")/.."
DEST="Sources/SilentFit/Resources/Module.xcassets"
BASE="https://raw.githubusercontent.com/google/material-design-icons/master/symbols/ios"
failed=0
while read -r sf material fill; do
  [[ -z "${sf:-}" || "$sf" == \#* ]] && continue
  variant="${material}"
  [[ "$fill" == "1" ]] && variant="${material}_fill1"
  dir="$DEST/$sf.symbolset"
  mkdir -p "$dir"
  if ! curl -sfL "$BASE/$material/materialsymbolsrounded/${variant}_symbol.svg" -o "$dir/$sf.svg"; then
    echo "missing: $sf -> $material ($variant)" >&2
    rm -rf "$dir"
    failed=1
    continue
  fi
  cat > "$dir/Contents.json" <<JSON
{
  "info" : { "author" : "xcode", "version" : 1 },
  "symbols" : [ { "filename" : "$sf.svg", "idiom" : "universal" } ]
}
JSON
done < Tools/symbols.txt
exit $failed
