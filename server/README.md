# Серверная часть Larpgram

Всё, что крутится на домашнем сервере (Debian, `lemonke67@100.115.48.43` через Tailscale) ради
приложения. Секреты (`.env`) в git не кладём — только `.env.sample` / `.env.example`.

| Папка | Что | Где на сервере | Адрес |
| --- | --- | --- | --- |
| `key-escrow/` | ключ восстановления для новых сессий, «удалить у обоих» | `/srv/secure/larpgram/key-escrow` | `push.mango-kokos.ru/escrow` |
| `account/` | регистрация и сброс пароля из приложения (код на почту, аккаунты — в MAS) | `/srv/secure/larpgram/larpgram-account` | `push.mango-kokos.ru/account` |
| `gif-proxy/` | прокси поиска GIF (Giphy), ключ API на сервере | `/srv/secure/larpgram/gif-proxy` | `gifs.mango-kokos.ru` |
| `stickers/` | nginx со стикерпаками (только конфиг; паки лежат рядом на сервере) | `/srv/secure/larpgram/stickers` | `stickers.mango-kokos.ru` |
| `tg-import/` | импорт стикерпаков из Telegram (ходит наружу через sing-box) | `/srv/secure/larpgram/tg-import` | `stickers.mango-kokos.ru/import` |

Каталоги сервисов лежат на зашифрованном разделе `/srv/secure` (LUKS, ключ — от Tang на RPi5;
этап 6 `larpgram-infra`, 2026-10-07). Данные key-escrow и account — в `./data` каталога проекта,
не в docker-томах. Matrix — там же (`/matrix` → `/srv/secure/matrix`). Если после перезагрузки
Pi недоступна, раздел открывается вручную: `sudo /usr/local/sbin/secure-unlock.sh manual`.

Маршруты Traefik, которые плейбук не ставит (запрет admin API MAS и Synapse снаружи), живут в
`larpgram-infra/traefik/dynamic`, на сервере — `/etc/traefik-main/dynamic` (открытый диск: Traefik
стартует раньше, чем открывается `/srv/secure`). Вход по паролю → MAS с этапа 3 маршрутизирует сам
плейбук, старый `matrix-mas-compat.yml` больше не нужен.

Не в этом репозитории, но приложение от них зависит: sygnal (push-шлюз, под MDAD) и
`smtp-tunnel` (почта на Proton) — оба в `larpgram-infra`. tg-import и smtp-tunnel ходят наружу
через sing-box на хосте (`172.17.0.1:7891`): если его выход мёртв, импорт отвечает
`502 upstream_unreachable`. Подробности — `docs/05-infra-roadmap.md`.

Если SSH рвётся до баннера (`kex_exchange_identification: Connection closed`), на Маке не запущен
Tailscale.

Сайт с APK (`larpgram.mango-kokos.ru`) — отдельный git-репозиторий `~/element-fork/larpgram-site`
с деплоем через `git push deploy main`; выкатка версии — `docs/RELEASE.md`.

Выкатка всех сервисов отсюда — из `larpgram-infra` (с 2026-10-06): закоммитить правку, затем
`just services <папка-на-сервере>` (`key-escrow`, `larpgram-account`, `gif-proxy`, `tg-import`,
`stickers`) или `just services` для всех. Исходники берутся из коммита, `.env` из ansible-vault,
сервис запускает systemd (`larpgram@<папка>.service`, логи в `journalctl`). Вручную через
`docker compose up -d` больше не запускать: в compose `restart: "no"`, и такой контейнер не
переживёт перезагрузку. Подробности сервисов — `key-escrow/DEPLOY.md`, `account/DEPLOY.md`.
