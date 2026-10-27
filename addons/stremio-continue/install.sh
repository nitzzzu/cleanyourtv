#!/usr/bin/env bash
# Build Continue Watching (Stremio), install it on the TV and grant it Stremio's rows in the TV provider.
# Usage: TV_HOST=<tv ip> addons/stremio-continue/install.sh
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address}"
ROOT="$(cd ../.. && { pwd -W 2>/dev/null || pwd; })"
PY="$ROOT/tools/venv/bin/python"
[ -x "$PY" ] || PY="$ROOT/tools/venv/Scripts/python.exe"
export MSYS_NO_PATHCONV=1
APK=app/build/outputs/apk/release/app-release.apk
if [ "${SKIP_BUILD:-}" != 1 ]; then ./gradlew assembleRelease -q --console=plain; fi
"$PY" -I "$ROOT/tools/tv.py" --push "$APK" /data/local/tmp/addon.apk
"$PY" -I "$ROOT/tools/tv.py" 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
"$PY" -I "$ROOT/tools/tv.py" 'pm grant io.github.nitzzzu.stremiocontinue android.permission.READ_TV_LISTINGS'
echo "Installed. Press Home and answer Show card."
