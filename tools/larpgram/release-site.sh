#!/usr/bin/env bash
# Выкатка release-APK на larpgram.mango-kokos.ru: одна команда вместо ручных шагов, в которых
# легко забыть sha256 (без него — или с устаревшим — обновление в приложении у всех ломается).
#
#   tools/larpgram/release-site.sh <release.apk>            # подготовить: проверить и обновить сайт
#   tools/larpgram/release-site.sh <release.apk> --deploy   # и выкатить (scp + git push deploy)
#
# Что делает:
#  1. Проверяет подпись: сертификат должен быть наш (SHA-256 de1af8ea…), иначе поверх
#     установленных у людей сборок APK не встанет.
#  2. Берёт versionCode/versionName из APK, требует versionCode больше, чем в latest.json.
#  3. Пишет в ../larpgram-site/html/latest.json все поля (versionCode, versionName, apkUrl,
#     sha256) и меняет номер версии и размер в index.html (три места).
#  4. С --deploy: на сервере larpgram.apk → larpgram-prev.apk, заливает новый APK, коммитит сайт,
#     `git push deploy main`, проверяет, что сайт отдаёт новый манифест и файл с тем же sha256.
# Блок «Что нового» в index.html пишется руками — скрипт напомнит.
set -euo pipefail

APK="${1:-}"
DEPLOY="${2:-}"
[[ -f "$APK" ]] || { echo "usage: $0 <release.apk> [--deploy]" >&2; exit 1; }

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
SITE="$(cd "$REPO/../larpgram-site" && pwd)"
SERVER="lemonke67@100.115.48.43"
# Если Tailscale до сервера не пускает по SSH, можно идти по другому адресу с тем же ключом хоста:
#   LARPGRAM_SSH_OPTS="-o HostName=94.103.236.41 -o HostKeyAlias=100.115.48.43" release-site.sh ...
read -r -a SSH_OPTS <<<"${LARPGRAM_SSH_OPTS:-}"
SERVER_HTML="/mnt/data/larpgram-site/html"
CERT_PREFIX="de1af8ea"

BUILD_TOOLS="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* | sort -V | tail -1)"
APKSIGNER="$BUILD_TOOLS/apksigner"
AAPT2="$BUILD_TOOLS/aapt2"
export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jdk-21.0.12+8/Contents/Home}"

cert="$("$APKSIGNER" verify --print-certs "$APK" | awk -F': ' '/certificate SHA-256 digest/ {print $NF; exit}')"
[[ "$cert" == "$CERT_PREFIX"* ]] || { echo "Чужая подпись ($cert), нужен release с keystore.properties" >&2; exit 1; }

badging="$("$AAPT2" dump badging "$APK" | awk 'NR == 1')"
version_code="$(sed -E "s/.*versionCode='([0-9]+)'.*/\1/" <<<"$badging")"
version_name="$(sed -E "s/.*versionName='([^']+)'.*/\1/" <<<"$badging")"
sha256="$(shasum -a 256 "$APK" | cut -d' ' -f1)"
size_mb="$(( $(stat -f%z "$APK") / 1048576 ))"

# Сравниваем с выкаченным (закоммиченным) манифестом: подготовка без --deploy уже меняет рабочую копию.
current_code="$(git -C "$SITE" show HEAD:html/latest.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["versionCode"])')"
(( version_code > current_code )) || { echo "versionCode $version_code не больше текущего $current_code" >&2; exit 1; }

python3 - "$SITE/html/latest.json" "$version_code" "$version_name" "$sha256" <<'PY'
import json, sys
path, code, name, sha = sys.argv[1:]
data = json.load(open(path))
data.update(versionCode=int(code), versionName=name, sha256=sha,
            apkUrl="https://larpgram.mango-kokos.ru/larpgram.apk")
open(path, "w").write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
PY

python3 - "$SITE/html/index.html" "$version_name" "$size_mb" <<'PY'
import re, sys
path, name, size = sys.argv[1:]
html = open(path).read()
html, n1 = re.subn(r'(<b id="ver">)[^<]*(</b>)', rf'\g<1>{name}\g<2>', html)
html, n2 = re.subn(r'(<b id="size">)[^<]*(</b>)', rf'\g<1>≈{size} МБ\g<2>', html)
# Под «Что нового» история версий: номер меняем только у верхнего блока.
html, n3 = re.subn(r'(<p class="wn-ver">Версия )[^<]*(</p>)', rf'\g<1>{name}\g<2>', html, count=1)
html, n4 = re.subn(r'Larpgram v[0-9][^ <]*', f'Larpgram v{name}', html)
if (n1, n2, n3, n4) != (1, 1, 1, 1):
    sys.exit(f"index.html: ожидал по одной замене, вышло {(n1, n2, n3, n4)}")
open(path, "w").write(html)
PY

echo "Сайт подготовлен: $version_name ($version_code), sha256 $sha256, ≈$size_mb МБ"
echo "Не забудь блок «Что нового» в $SITE/html/index.html"

[[ "$DEPLOY" == "--deploy" ]] || { echo "Выкатка: $0 $APK --deploy"; exit 0; }

ssh ${SSH_OPTS[@]+"${SSH_OPTS[@]}"} "$SERVER" "cd $SERVER_HTML && cp -p larpgram.apk larpgram-prev.apk"
scp ${SSH_OPTS[@]+"${SSH_OPTS[@]}"} "$APK" "$SERVER:$SERVER_HTML/larpgram.apk.tmp"
ssh ${SSH_OPTS[@]+"${SSH_OPTS[@]}"} "$SERVER" "cd $SERVER_HTML && mv larpgram.apk.tmp larpgram.apk"
git -C "$SITE" add html/latest.json html/index.html
git -C "$SITE" commit -m "release $version_name"
GIT_SSH_COMMAND="ssh ${LARPGRAM_SSH_OPTS:-}" git -C "$SITE" push deploy main

remote_sha="$(curl -s https://larpgram.mango-kokos.ru/larpgram.apk | shasum -a 256 | cut -d' ' -f1)"
manifest_sha="$(curl -s https://larpgram.mango-kokos.ru/latest.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["sha256"])')"
[[ "$remote_sha" == "$sha256" && "$manifest_sha" == "$sha256" ]] \
  || { echo "ВНИМАНИЕ: сайт отдаёт не то (apk $remote_sha, манифест $manifest_sha)" >&2; exit 1; }
echo "Выкачено: $version_name, сайт отдаёт нужный APK и манифест."
