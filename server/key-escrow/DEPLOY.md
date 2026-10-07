# Развёртывание key-escrow

Сервис депонирования ключа восстановления Larpgram. Главный путь с 2026-09-24: новая сессия
сама забирает ключ (`GET /key/session`) и открывает историю — вход в аккаунт и есть
подтверждение, как в Telegram. Вход по коду с почты (`/code`, `/key/redeem`) остался запасным.
Второе назначение — «удалить у обоих» для личек (`POST /room/delete`).

Исходники — в репозитории Larpgram, `server/key-escrow` (с 2026-09-28; до этого было две копии
без git). На сервере — `/srv/secure/larpgram/key-escrow` (зашифрованный раздел), выкатывает
`larpgram-infra` (см. «Обновление»). Живёт на `push.mango-kokos.ru/escrow` (рядом с sygnal, не на
публичной витрине `larpgram.mango-kokos.ru`).

> **Модель безопасности.** Сервис хранит ключ восстановления (зашифрованным на своём
> master-ключе) и отдаёт его любой сессии, вошедшей в аккаунт (`/key/session`, с 2026-09-24),
> а раньше — только по коду с почты. Вход в аккаунт = доступ к истории, как в Telegram. Значит, сервер и тот, у кого доступ к почте
> аккаунта, технически могут получить ключ и читать переписку. Это осознанный компромисс
> ради телеграмного UX (см. README форка, раздел про вход по коду). Master-ключ хранить как
> keystore — копию в Vaultwarden.

## Что понадобится на сервере

- DNS: **ничего нового**, `push.mango-kokos.ru` уже существует (sygnal). Мы вешаемся на путь.
- Traefik `traefik-main`, сеть `web`, certresolver `myresolver` — как у остальных сервисов.
- Контейнер `matrix-exim-relay` и его сеть (для отправки кода той же почтой, что у MAS).

## 1. Секреты

`.env` собирается из ansible-vault `larpgram-infra` (шаблон `services/env/key-escrow.env.j2`) и
перезаписывается при каждой выкатке, руками на сервере его не правим. Переменные vault:
`vault_escrow_master_key` (ровно 32 байта base64, `openssl rand -base64 32`),
`vault_escrow_code_pepper` (`openssl rand -hex 32`), `vault_escrow_synapse_admin_token` (см.
«Удалить у обоих»). Правка — `just vault-edit` в `larpgram-infra`.

**Master-ключ хранится ещё и в Vaultwarden:** его потеря = депонированные ключи не расшифровать
(вход по почте перестанет работать; вторым устройством и вводом ключа восстановления люди при
этом верифицируются по-прежнему).

## 2. Почта: ничего настраивать не надо

Контейнер escrow подключён к сети `matrix-exim-relay` и шлёт код на `matrix-exim-relay:8025`
— тот же релей, что использует MAS для регистрации и сброса пароля. Релей уже принимает почту
от контейнеров своей сети, править его конфиг не требуется.

Проверка после запуска: `POST /escrow/code` должен вернуть 200, а в логах релея
(`docker logs matrix-exim-relay`) — приём и доставку письма.

## 3. Сборка и запуск

`just services key-escrow` в `larpgram-infra` (см. «Обновление»): сборка, `.env`, перезапуск
`larpgram@key-escrow.service`. Логи — `journalctl -u larpgram@key-escrow` (строка «key-escrow
слушает :8080»). Вручную `docker compose up -d` не запускать: в compose `restart: "no"`, такой
контейнер не переживёт перезагрузку и не дождётся `/srv/secure`.

Traefik подхватит контейнер по лейблам (сеть `web`). Если маршрут не поднялся (свежий контейнер,
старые IP в Traefik) — знакомая грабля: `docker restart traefik-main`, подожди 1-2 минуты.

## 4. Проверка

```bash
# health через Traefik (префикс /escrow срезается, приложение видит /health):
curl -s https://push.mango-kokos.ru/escrow/health
# -> {"ok":true}

# sygnal на том же хосте не должен пострадать (эндпоинт пушей отвечает как раньше):
curl -s -o /dev/null -w "%{http_code}\n" https://push.mango-kokos.ru/_matrix/push/v1/notify
```

Сквозная проверка с настоящим токеном (взять access-токен из приложения или логином):

```bash
TOKEN=<matrix access token>
# залить ключ (обычно это делает само приложение при включении бэкапа):
curl -s -X PUT https://push.mango-kokos.ru/escrow/key \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"recovery_key":"<настоящий ключ, 48 знаков base58>"}' -w "\n%{http_code}\n"      # 204, мусор — 400

curl -s https://push.mango-kokos.ru/escrow/key -H "Authorization: Bearer $TOKEN" -w "\n%{http_code}\n"  # 200
curl -s -X POST https://push.mango-kokos.ru/escrow/code -H "Authorization: Bearer $TOKEN" -w "\n%{http_code}\n"  # 200 + masked_email, письмо на почту
curl -s -X POST https://push.mango-kokos.ru/escrow/key/redeem \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"code":"123456"}' -w "\n%{http_code}\n"    # 200 + recovery_key при верном коде
```

## Эндпоинты

| Метод | Путь (после Traefik) | Назначение | Коды |
| --- | --- | --- | --- |
| GET | `/key` | лежит ли ключ | 200 / 404 |
| PUT | `/key` | залить ключ (только формат ключа Matrix) | 204 / 400 |
| DELETE | `/key` | удалить протухший ключ (клиент зовёт, если ключ не подошёл) | 204 |
| POST | `/code` | прислать код на почту | 200 (`masked_email`) / 404 нет почты / 429 часто |
| POST | `/key/redeem` | проверить код, отдать ключ | 200 / 400 (`attempts_left`) / 404 нет ключа / 410 истёк / 429 лимит |
| GET | `/key/session` | отдать ключ вошедшей сессии без кода (авто-разблокировка при входе) | 200 / 404 нет ключа |
| POST | `/room/delete` | удалить ЛС у обоих (Synapse purge) | 202 / 400 нет room_id / 403 не участник / 409 не ЛС (`{"error": причина}`) / 502 Synapse / 503 нет admin-токена |

Все требуют `Authorization: Bearer <matrix token>`; сервис проверяет его через Synapse
`whoami` (401 — токен плохой, 502 — Synapse недоступен или 429) и берёт почту из `/account/3pid`.
Чтения, записи и удаления ключа пишутся в лог контейнера строками `{"audit": ...}` (кто, с какого
устройства, когда; без ключей): `docker logs key-escrow | grep audit`. Код: 6 цифр, живёт 10 минут, 5 попыток, повторный
запрос не чаще раза в минуту (параметры в `.env`).

## Удалить у обоих (admin-токен, 2026-08-28)

Эндпоинт `POST /room/delete` сносит комнату целиком через Synapse admin API — так реализовано
«удалить у обоих» для ЛС (Matrix сам удалить чужую сторону не даёт). С 2026-09-28 сервис не
верит клиенту и проверяет всё сам через admin API (`lib/rules.js`, тесты — `npm test`):
вызывающий — участник; участников не больше двух, все с нашего сервера; у комнаты нет имени и
адреса; `events_default` = 0 (не канал); комната есть в `m.direct` **у каждого** участника.
Иначе 409 с причиной. Затем `DELETE /_synapse/admin/v2/rooms/<id>` с `purge:true`. Клиент дёргает
эндпоинт после 5-секундной undo-плашки и не предлагает его для собеседников с других серверов
(purge сносит только нашу копию).

Нужен `SYNAPSE_ADMIN_TOKEN` в `.env`. **Грабля MAS:** флаг `admin=1` в БД Synapse доступа к admin API
НЕ даёт (403) — при делегировании auth в MAS нужен токен с admin-scope, его даёт флаг при выпуске
(админом MAS аккаунт быть не обязан).

С 2026-09-29 (аудит C-017) токен принадлежит отдельному боту **`@escrow-admin`** (device
`ESCROWADMIN`, без пароля — войти в него можно только по токену), а не личному аккаунту владельца:
утечка `.env` не даёт писать от имени владельца, а чистка своих сессий не ломает escrow. Выпуск
заново:

```bash
# один раз: аккаунт бота
docker exec matrix-authentication-service mas-cli manage register-user --yes --no-admin -d "Escrow admin" escrow-admin
# токен с admin-scope
docker exec matrix-authentication-service mas-cli manage issue-compatibility-token \
  --yes-i-want-to-grant-synapse-admin-privileges escrow-admin ESCROWADMIN 2>&1 | grep -o 'mct_[A-Za-z0-9_]*' | head -1
```

Токен — в `vault_escrow_synapse_admin_token` (`just vault-edit`), затем
`just services key-escrow` (env читается только при старте).

Отозвать — удалить сессию `ESCROWADMIN` бота (`mas-cli manage kill-sessions escrow-admin`). Пусто в
env → эндпоинт отвечает 503. Проверка scope:

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  'https://matrix.mango-kokos.ru/_synapse/admin/v1/rooms?limit=1' \
  -H "Authorization: Bearer $TOKEN"     # 200 = admin ок, 403 = токен без scope
```

## Обновление

С 2026-10-06 выкатывает `larpgram-infra` (этап 5): исходники из коммита этого репозитория,
`.env` из ansible-vault, сборка и перезапуск `larpgram@key-escrow.service`. Сначала закоммитить,
потом с Mac:

```bash
(cd server/key-escrow && npm test)
(cd ~/element-fork/larpgram-infra && just services key-escrow)
curl -s https://push.mango-kokos.ru/escrow/health      # {"ok":true}
```

Секреты из раздела 1 теперь в vault `larpgram-infra` (`vault_escrow_*`), `.env` на сервере
перезаписывается при каждой выкатке. Логи: `journalctl -u larpgram@key-escrow`.

Данные (`./data` в каталоге проекта на зашифрованном `/srv/secure`) переживают пересборку. Схема БД создаётся сама при старте.
`.dockerignore` не пускает `.env` и базу в образ; старые образы (до 2026-09-28 в них был `.env`)
удалены.

## Бэкап

Общий ночной бэкап `larpgram-infra` (`backup/backup.sh`, 04:30): SQLite backup API внутри
контейнера, копия шифруется age и уходит в приватный GitHub-репозиторий вместе с базами Matrix.
Свой `backup.sh` с открытыми копиями в `~/backups/key-escrow` убран на этапе 6 (2026-10-07).
Ключи в копии к тому же зашифрованы `ESCROW_MASTER_KEY` (копия в Vaultwarden). Восстановление —
`larpgram-infra/backup/restore.md`: `sudo systemctl stop larpgram@key-escrow`, положить файл как
`/srv/secure/larpgram/key-escrow/data/escrow.db` (владелец uid 1000), `sudo systemctl start
larpgram@key-escrow`.

Без бэкапа потеря тома опасна: провижинер решит, что ключей нет ни у кого, и молча выпустит
всем новые ключи восстановления.
