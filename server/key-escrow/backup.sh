#!/bin/sh
# Ночной бэкап escrow.db (cron пользователя, 04:10). Копия снимается SQLite backup API внутри
# контейнера (консистентно при WAL) и уезжает на NVMe в ~/backups/key-escrow: другой диск, чем
# Docker-том на RAID. Хранится 14 дней. Ключи в базе зашифрованы ESCROW_MASTER_KEY из .env
# (копия в Vaultwarden) — без него бэкап бесполезен, с ним — восстанавливает всех.
set -eu
DEST="$HOME/backups/key-escrow"
mkdir -p "$DEST"
chmod 700 "$DEST"
STAMP=$(date +%Y%m%d)
docker exec -w /app key-escrow node -e "
const Database = require('better-sqlite3');
new Database('/data/escrow.db', { readonly: true }).backup('/data/escrow-backup.db')
  .then(() => process.exit(0))
  .catch((e) => { console.error(e); process.exit(1); });
"
docker cp key-escrow:/data/escrow-backup.db "$DEST/escrow-$STAMP.db"
docker exec key-escrow rm -f /data/escrow-backup.db
chmod 600 "$DEST/escrow-$STAMP.db"
find "$DEST" -name 'escrow-*.db' -mtime +14 -delete
