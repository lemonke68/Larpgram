# Развёртывание key-escrow

Сервис депонирования ключа восстановления Larpgram. Даёт вход на новом устройстве по коду
с почты вместо второго устройства. Живёт на `push.mango-kokos.ru/escrow` (рядом с sygnal,
не на публичной витрине `larpgram.mango-kokos.ru`).

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

```bash
cd ~/key-escrow            # или куда положишь бандл
cp .env.sample .env
# master-ключ шифрования (ровно 32 байта base64):
echo "ESCROW_MASTER_KEY=$(openssl rand -base64 32)" >> .env   # впиши в .env вместо пустого
# пеппер для хэша кодов:
echo "CODE_PEPPER=$(openssl rand -hex 32)" >> .env
```

Открой `.env`, убедись, что `ESCROW_MASTER_KEY` и `CODE_PEPPER` заполнены (строки-заготовки
сверху удали, чтобы не было дублей). **Master-ключ сразу сохрани в Vaultwarden:** его потеря
= депонированные ключи не расшифровать (вход по почте перестанет работать; вторым устройством
и вводом ключа восстановления люди при этом верифицируются по-прежнему).

## 2. Почта: ничего настраивать не надо

Контейнер escrow подключён к сети `matrix-exim-relay` и шлёт код на `matrix-exim-relay:8025`
— тот же релей, что использует MAS для регистрации и сброса пароля. Релей уже принимает почту
от контейнеров своей сети, править его конфиг не требуется.

Проверка после запуска: `POST /escrow/code` должен вернуть 200, а в логах релея
(`docker logs matrix-exim-relay`) — приём и доставку письма.

## 3. Сборка и запуск

```bash
cd ~/key-escrow
docker compose build
docker compose up -d
docker compose logs -f key-escrow      # должно быть «key-escrow слушает :8080»
```

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
  -d '{"recovery_key":"TEST test test ..."}' -w "\n%{http_code}\n"      # 204

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
| PUT | `/key` | залить ключ | 204 |
| POST | `/code` | прислать код на почту | 200 (`masked_email`) / 404 нет почты / 429 часто |
| POST | `/key/redeem` | проверить код, отдать ключ | 200 / 400 (`attempts_left`) / 404 нет ключа / 410 истёк / 429 лимит |
| GET | `/key/session` | отдать ключ вошедшей сессии без кода (авто-разблокировка при входе) | 200 / 404 нет ключа |
| POST | `/room/delete` | удалить ЛС у обоих (Synapse purge) | 202 / 400 нет room_id / 403 не участник / 409 не ЛС (нет в m.direct или больше двух) / 503 нет admin-токена |

Все требуют `Authorization: Bearer <matrix token>`; сервис проверяет его через Synapse
`whoami` и берёт почту из `/account/3pid`. Код: 6 цифр, живёт 10 минут, 5 попыток, повторный
запрос не чаще раза в минуту (параметры в `.env`).

## Удалить у обоих (admin-токен, 2026-08-28)

Эндпоинт `POST /room/delete` сносит комнату целиком через Synapse admin API — так реализовано
«удалить у обоих» для ЛС (Matrix сам удалить чужую сторону не даёт). Проверяет, что комната — ЛС
(ровно 2 участника) и что вызывающий в ней состоит; затем `DELETE /_synapse/admin/v2/rooms/<id>`
с `purge:true`. Клиент дёргает его после 5-секундной undo-плашки.

Нужен `SYNAPSE_ADMIN_TOKEN` в `.env`. **Грабля MAS:** флаг `admin=1` в БД Synapse доступа к admin API
НЕ даёт (403) — при делегировании auth в MAS нужен токен с admin-scope. Выпуск (пользователь должен
быть в `mas-cli manage list-admin-users`; при необходимости `mas-cli manage promote-admin <user>`):

```bash
docker exec matrix-authentication-service \
  mas-cli manage issue-compatibility-token \
  --yes-i-want-to-grant-synapse-admin-privileges lemonke67
# -> mct_...  впиши в SYNAPSE_ADMIN_TOKEN= в ~/key-escrow/.env, затем пересобери сервис
```

Токен — это compat-токен @lemonke67 (полный доступ + synapse admin), отзывается через mas-cli
(`kill-sessions` / удаление сессии). Пусто в env → эндпоинт отвечает 503. Проверка scope:

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  'https://matrix.mango-kokos.ru/_synapse/admin/v1/rooms?limit=1' \
  -H "Authorization: Bearer $TOKEN"     # 200 = admin ок, 403 = токен без scope
```

## Обновление

```bash
cd ~/key-escrow && git pull        # или залей новый бандл
docker compose build && docker compose up -d
```

Данные (`escrow-data` volume) переживают пересборку. Схема БД создаётся сама при старте.
