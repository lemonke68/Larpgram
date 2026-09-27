# Серверная часть Larpgram

Всё, что крутится на домашнем сервере (Debian, `lemonke67@100.115.48.43` через Tailscale) ради
приложения. Секреты (`.env`) в git не кладём — только `.env.sample` / `.env.example`.

| Папка | Что | Где на сервере | Адрес |
| --- | --- | --- | --- |
| `key-escrow/` | ключ восстановления для новых сессий, «удалить у обоих» | `~/key-escrow` | `push.mango-kokos.ru/escrow` |
| `gif-proxy/` | прокси поиска GIF (Giphy), ключ API на сервере | `~/gif-proxy` | `gifs.mango-kokos.ru` |
| `stickers/` | nginx со стикерпаками (только конфиг; паки лежат рядом на сервере) | `/matrix/stickers` (root) | `stickers.mango-kokos.ru` |

Сайт с APK (`larpgram.mango-kokos.ru`) — отдельный git-репозиторий `~/element-fork/larpgram-site`
с деплоем через `git push deploy main`; выкатка версии — `docs/RELEASE.md`.

Выкатка key-escrow и бэкап — `key-escrow/DEPLOY.md`. gif-proxy и stickers меняются редко:
правка → `rsync` в папку на сервере → `docker compose up -d --build` там же (stickers — через
sudo, папка root).
