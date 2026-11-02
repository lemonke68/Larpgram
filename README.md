# Larpgram

Мессенджер для сервера mango-kokos.ru: то же, к чему все привыкли в Telegram, но на своём
Matrix-сервере. Стикеры, гифки, кружочки, работающие уведомления и вход без выбора сервера.

Основан на [Element X Android](https://github.com/element-hq/element-x-android) (AGPL-3.0).

## Чем отличается от Element X

- **Свой сервер и только он.** Выбор сервера из онбординга убран, регистрация оставлена.
- **Стикеры.** Свой пикер, паки по MSC2545 плюс импорт стикер-паков из Telegram по имени
  прямо из приложения.
- **Гифки.** Поиск через свой прокси, недавно отправленные под рукой.
- **Кружочки.** Круглые видеосообщения, как в Telegram (в работе).
- **Уведомления** идут через свой push-шлюз, а не через matrix.org.

## Работа с устройствами и аудио

Ветка `preweb-device-audio`, инструкция и критерии: [старт работы](docs/larpgram/README.md).

## Сборка

Нужны JDK 21, Android SDK и собственный локальный SDK AAR. Перед первой сборкой
выполнить [подготовку SDK](tools/larpgram/rust-sdk/README.md); для встроенного
Element Call нужны Node.js 24 и pnpm 11.

```
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew :app:assembleGplayDebug
```

APK появится в `app/build/outputs/apk/gplay/debug/`, для телефона нужен вариант
`arm64-v8a`. Собирать надо **gplay**: уведомления работают только в нём.

Часть возможностей опирается на сервисы, поднятые на своём сервере (прокси гифок, импорт
стикер-паков, push-шлюз). Без них приложение соберётся и запустится, но эти функции будут
недоступны.

## Лицензия

AGPL-3.0-only, как и у Element X, на котором основан проект. Исходный код открыт,
уведомления об авторстве сохранены в [LICENSE](LICENSE) и [AUTHORS.md](AUTHORS.md).

Copyright © 2025 Element Creations Ltd, New Vector Ltd и участники Element X.
Изменения Larpgram © 2026 lemonke67.
