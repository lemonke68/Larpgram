# Серверная часть Larpgram

Всё, что крутится на домашнем сервере (Debian, `lemonke67@100.115.48.43` через Tailscale) ради
приложения. Секреты (`.env`) в git не кладём — только `.env.sample` / `.env.example`.

| Папка | Что | Где на сервере | Адрес |
| --- | --- | --- | --- |
| `key-escrow/` | ключ восстановления для новых сессий, «удалить у обоих» | `~/key-escrow` | `push.mango-kokos.ru/escrow` |
| `account/` | регистрация и сброс пароля из приложения (код на почту, аккаунты — в MAS) | `~/larpgram-account` | `push.mango-kokos.ru/account` |
| `gif-proxy/` | прокси поиска GIF (Giphy), ключ API на сервере | `~/gif-proxy` | `gifs.mango-kokos.ru` |
| `stickers/` | nginx со стикерпаками (только конфиг; паки лежат рядом на сервере) | `~/stickers` | `stickers.mango-kokos.ru` |
| `tg-import/` | импорт стикерпаков из Telegram (ходит наружу через sing-box) | `~/tg-import` | `stickers.mango-kokos.ru/import` |
| `traefik/` | маршруты Traefik, которые плейбук не ставит: вход по паролю → MAS, запрет admin API снаружи | `/matrix/traefik/config/dynamic` (root) | `matrix.mango-kokos.ru` |

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
