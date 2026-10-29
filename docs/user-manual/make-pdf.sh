#!/usr/bin/env bash
# Prints the user manual to the PDF the app's Help menu opens. Run it after changing user-manual.html.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
out="$here/../../desktop/manual/MindMapView-User-Manual.pdf"
chrome="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"

"$chrome" --headless --disable-gpu --no-pdf-header-footer --allow-file-access-from-files \
    --print-to-pdf="$out" "file://$here/user-manual.html" 2>/dev/null
echo "Wrote $out"
