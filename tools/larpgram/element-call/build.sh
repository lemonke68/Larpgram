#!/usr/bin/env bash
# Larpgram: rebuild the pinned embedded call UI from source; no publishing.
set -euo pipefail
base_aar="$1"
output_aar="$2"
source_cache="$3"
script_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
patch_file="$script_dir/interface.patch"
revision=9f7c35cd8f84b12f9395e6aac56fd396a3921482
patch_hash="$(shasum -a 256 "$patch_file" | cut -d ' ' -f 1)"
source_dir="$source_cache/$patch_hash"
command -v node >/dev/null || { echo 'Element Call requires Node.js 24 and pnpm 11.' >&2; exit 1; }
command -v pnpm >/dev/null || { echo 'Element Call requires pnpm 11.' >&2; exit 1; }
if [ ! -f "$source_dir/.git/larpgram-patched" ]; then
    if [ -e "$source_dir" ]; then
        echo "Incomplete generated Element Call checkout: $source_dir" >&2
        exit 1
    fi
    mkdir -p "$source_cache"
    git clone --depth 1 --branch v0.26.1 https://github.com/element-hq/element-call.git "$source_dir"
    [ "$(git -C "$source_dir" rev-parse HEAD)" = "$revision" ] || { echo 'Unexpected upstream revision.' >&2; exit 1; }
    git -C "$source_dir" apply --check "$patch_file"
    git -C "$source_dir" apply "$patch_file"
    touch "$source_dir/.git/larpgram-patched"
fi
(
    cd "$source_dir"
    pnpm install --frozen-lockfile
    # Build locally. CI upload plugins must never be enabled by inherited credentials.
    unset SENTRY_AUTH_TOKEN CODECOV_TOKEN
    NODE_OPTIONS=--max-old-space-size=4096 pnpm exec vite build --config vite-embedded.config.ts
)
python3 - "$base_aar" "$output_aar" "$source_dir/dist" <<'PY'
import os, pathlib, sys, zipfile
base, output, dist = sys.argv[1:]
pathlib.Path(output).parent.mkdir(parents=True, exist_ok=True)
temporary = output + '.tmp'
with zipfile.ZipFile(base) as upstream, zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as result:
    for entry in upstream.infolist():
        if not entry.filename.startswith('assets/element-call/') and not entry.is_dir():
            result.writestr(entry, upstream.read(entry))
    for source in sorted(pathlib.Path(dist).rglob('*')):
        if source.is_file():
            result.write(source, 'assets/element-call/' + source.relative_to(dist).as_posix())
os.replace(temporary, output)
PY
