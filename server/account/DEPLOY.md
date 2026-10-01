# account: регистрация и сброс пароля из приложения

Сервис для экранов входа Larpgram: регистрация с кодом на почту и сброс пароля, без браузерных
форм MAS. Аккаунты и пароли хранит MAS; сервис обращается к его admin API. Сам вход приложение
делает обычным Matrix-запросом `POST /_matrix/client/v3/login` (слой совместимости MAS).

- Адрес: `https://push.mango-kokos.ru/account` (Traefik срезает `/account`).
- На сервере: `~/larpgram-account`, контейнер `larpgram-account`, том `account-data`.
- База (`/data/account.db`) хранит только заявки на несколько минут. Бэкап не нужен.

## API

Без авторизации. Ошибки — `{"error": "<код>"}`.

| Запрос | Тело | Ответ |
| --- | --- | --- |
| `GET /health` | | `{"ok":true}` |
| `POST /register/start` | `username`, `email` | `200 {ticket, resend_after}`; `400 username_too_short / username_too_long / username_invalid / email_invalid`; `409 username_taken / email_taken`; `502 mail_failed` |
| `POST /register/confirm` | `ticket`, `code`, `password` | `200 {username}`; `400 wrong_code {attempts_left} / password_too_short / password_too_long / password_weak`; `409 username_taken / email_taken`; `410 code_expired`; `429 too_many_attempts` |
| `POST /password/forgot` | `login` (ник или почта) | `200 {ticket, resend_after}` всегда, есть аккаунт с почтой или нет |
| `POST /code/check` | `ticket`, `code` | `204`; `400 wrong_code`; `410`; `429` |
| `POST /password/reset` | `ticket`, `code`, `password` | `200 {username}`; ошибки как у `/register/confirm` |
| `POST /login/resolve` | `email` | `200 {username}`; `404 not_found` — вход по почте: MAS принимает только ник |
| `POST /login/start` | `login` (ник или почта), `password` | `200 {code_required: true, ticket, resend_after, username, email_hint}` — код ушёл на почту аккаунта; `200 {code_required: false, username}` — у аккаунта нет почты; `403 invalid_credentials`; `429 too_many_requests`; `502 mail_failed` |
| `POST /login/confirm` | `ticket`, `code` | `200 {username}` — приложению можно входить обычным `/login`; ошибки кода как у `/register/confirm` |
| `POST /code/resend` | `ticket` | `200 {resend_after}`; `429 too_soon {retry_after}`; `410 code_expired` |

Ручки вошедшего устройства — с заголовком `Authorization: Bearer <токен Matrix>` (сервис проверяет
его через `whoami`), без него `401 unauthorized`:

| Запрос | Тело | Ответ |
| --- | --- | --- |
| `POST /pair/offer` | | `200 {code, expires_in}` — код для QR-кода привязки устройства |
| `POST /pair/status` | `code` | `200 {state: waiting / redeemed / expired}`; `404` — чужой или неизвестный код |
| `POST /email/start` | `email` | `200 {ticket, resend_after}`; `400 email_invalid`; `409 email_taken`; `502 mail_failed` |
| `POST /email/confirm` | `ticket`, `code` | `200 {email}`; `400 wrong_code`; `410`; `429` |
| `POST /sessions/end` | `device_id` | `204`; `404 not_found` — завершить сеанс устройства (в том числе своего) |

Без авторизации: `POST /pair/redeem` `{code, device_name}` → `200 {user_id, device_id, access_token,
homeserver_url}` или `410 code_expired`.

Привязка по QR: вошедшее устройство берёт код (`/pair/offer`, живёт 2 минуты, одноразовый) и
показывает его QR-кодом `larpgram-login:<код>`; новое сканирует и меняет код на сессию
(`/pair/redeem`). Сессию выпускает MAS как personal session (`POST /api/admin/v1/personal-sessions`)
с новым устройством в scope; имя устройству сервис ставит сам. Штатный `/logout` для такой сессии
MAS не принимает — приложение выходит через `/sessions/end`.

Общие: `429 too_many_requests` (лимит по IP или по адресу), `502 upstream_failed` (MAS недоступен).

Правила:
- ник — латиница, цифры, `_`, с буквы, 3–32 знака, хранится в нижнем регистре;
- пароль — от 8 знаков, стойкость проверяет MAS (`password_weak`). После верного кода заявка живёт
  ещё 15 минут: пароль можно исправить без нового кода;
- код — 6 цифр, 10 минут, 5 попыток, повтор не чаще раза в минуту;
- лимиты (в памяти процесса): 10 заявок в час с одного IP, 5 писем в час на один адрес, 40
  проверок кода в час с одного IP;
- сброс отвечает одинаково для существующего и несуществующего аккаунта и не ждёт отправки
  письма, чтобы по этой ручке нельзя было перебирать ники и адреса.

## Что нужно на сервере (один раз)

**1. Маршруты Traefik** (root). Файл `server/traefik/matrix-mas-compat.yml` из репозитория:

```bash
sudo cp ~/matrix-mas-compat.yml /matrix/traefik/config/dynamic/matrix-mas-compat.yml
```

Он отдаёт `/_matrix/client/*/{login,logout,refresh}` в MAS (с префиксом `/auth`) и закрывает
`/auth/api/admin` снаружи. Traefik подхватывает файл сам. Проверка:

```bash
curl -s https://matrix.mango-kokos.ru/_matrix/client/v3/login          # flows с m.login.password
curl -s -o /dev/null -w "%{http_code}\n" https://matrix.mango-kokos.ru/auth/api/admin/v1/users   # 403
```

**2. MAS** — в `vars.yml` плейбука на Маке (`~/matrix-docker-ansible-deploy`, блок «Larpgram
(2026-10-01)» в конце): `matrix_authentication_service_admin_api_enabled`,
`..._config_account_login_with_email_allowed`, клиент в `..._config_clients_custom` и его id в
`policy.data.admin_clients`. Применить:

```bash
cd ~/matrix-docker-ansible-deploy
just run-tags setup-matrix-authentication-service,start -K
```

MAS перезапустится на несколько секунд, сессии не слетают.

**3. Секреты сервиса** — `~/larpgram-account/.env` по `.env.sample`: `CODE_PEPPER` (случайная
строка), `MAS_CLIENT_ID` и `MAS_CLIENT_SECRET` — те же, что в `vars.yml`.

## Обновление

С Mac, из корня репозитория:

```bash
(cd server/account && npm test)
rsync -az --exclude .env --exclude node_modules --exclude '*.db*' \
  server/account/ lemonke67@100.115.48.43:larpgram-account/
ssh lemonke67@100.115.48.43 'cd ~/larpgram-account && docker compose build && docker compose up -d'
curl -s https://push.mango-kokos.ru/account/health      # {"ok":true}
```

## Проверка после выкатки

```bash
# Занятый ник — 409 username_taken, значит admin API MAS отвечает.
curl -s -X POST https://push.mango-kokos.ru/account/register/start \
  -H 'content-type: application/json' -d '{"username":"lemonke67","email":"x@example.com"}'
docker logs --tail 20 larpgram-account     # журнал: register-start / register-done / reset-*
```

`502 upstream_failed` и `MAS ответил 401/403` в логе — не тот секрет клиента или id клиента нет в
`policy.data.admin_clients`. `MAS ответил 404` — admin API не включён (шаг 2).

## Тесты

`npm test` (`node --test`): правила (`test/rules.test.js`) и приложение целиком с SQLite в памяти
и заглушками MAS и почты (`test/app.test.js`). Локально `better-sqlite3` собирается скриптом
установки — он разрешён в `package.json` (`allowScripts`).
