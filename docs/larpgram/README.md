# Старт работы: устройства, QR, звонки и аудио

Задача друга — пункт 5 перед вебом, включая исследование и необходимую реализацию
фазы 6.4. Подробные сценарии и критерии:
[HANDOFF-PREWEB-DEVICE-AUDIO-2026-11-01.md](HANDOFF-PREWEB-DEVICE-AUDIO-2026-11-01.md).

## Получить код

```sh
git clone --branch preweb-device-audio --single-branch https://github.com/lemonke68/Larpgram.git
cd Larpgram
git fetch origin interface-completion:refs/remotes/origin/interface-completion
git status --short
git rev-parse HEAD
```

Для существующего чистого клона:

```sh
git fetch origin
git switch --track origin/preweb-device-audio
```

База кандидата: `9f106376d3` (0.4.1). Ветка друга наследует её и эту инструкцию.
`fork-base` пока остаётся на 0.4.0; переключаться на неё для проверок не нужно.
Работать и коммитить только в `preweb-device-audio`. Не мержить самостоятельно
`fork-base` и не запускать публикацию/деплой. Отдельные рабочие ветки владельца
не являются частью задания. Автор коммитов друга — его собственная Git-учётка.

## Подготовить сборку

Нужны JDK 21, Python 3, Git, Git LFS, Android SDK (версии платформ/build tools
берутся из Gradle проекта), NDK **28.2.13676358**, Rust **1.99.0**,
cargo-ndk **4.1.2**, Node.js **24**, pnpm **11** и сеть для первого запуска.
SDK AAR не хранится в Git: сначала собрать закреплённый SDK.

```sh
git lfs install
git lfs pull
export JAVA_HOME=/absolute/path/to/jdk-21
export ANDROID_HOME=/absolute/path/to/Android/sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
rustup toolchain install 1.99.0 --profile minimal
rustup target add --toolchain 1.99.0 aarch64-linux-android
cargo +1.99.0 install cargo-ndk --version 4.1.2 --locked
export RUSTUP_TOOLCHAIN=1.99.0
python3 tools/larpgram/rust-sdk/build.py
./gradlew :app:assembleGplayDebug --no-daemon --max-workers=3
```

Эти команды готовят ARM64. Для другого ABI использовать `--target`
(`armv7-linux-androideabi`, `i686-linux-android`, `x86_64-linux-android`), предварительно
добавив соответствующий Rust target. Для всей матрицы — `--all-abis` и все четыре
target. Детали и проверки AAR: [SDK README](../../tools/larpgram/rust-sdk/README.md).
Не заменять локальный SDK опубликованным upstream AAR: в нём нет нужного API.

Element Call собирается автоматически Gradle из закреплённого исходника с патчем;
для него нужны Node/pnpm. [Инструкция](../../tools/larpgram/element-call/README.md).
На Mac с 16 ГБ запускать одну Gradle-сборку за раз.

APK: `app/build/outputs/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk`.
Перед установкой записать SHA-256 (`shasum -a 256` на macOS, `sha256sum` на Linux)
и проверить серийный номер (`adb devices -l`). Установка на выбранное устройство:

```sh
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk
```

Debug имеет отдельный пакет `ru.mangokokos.larpgram.debug`. При несовпадении подписи
не удалять существующую установку/данные для обхода ошибки: согласовать отдельное
тестовое устройство или передачу совместимой сборки с владельцем. Релизный ключ и
`keystore.properties` для этого задания не нужны и не передаются через Git.

## Выполнить и вернуть результат

1. Сверить [CURRENT.md](CURRENT.md), записать исходный коммит/APK и матрицу устройств.
2. Выполнить сценарии хэндоффа на согласованных тестовых аккаунтах. Android 12+
   нужен физический; эмулятор не закрывает FiiO, Bluetooth и реальные звонки.
3. Исправления делать небольшими коммитами; для каждого повторить исходный сценарий.
4. Заполнить [DEVICE-AUDIO-REPORT.md](DEVICE-AUDIO-REPORT.md). Логи и изображения
   складывать в `docs/larpgram/evidence/device-audio/` после удаления секретов и
   приватного содержимого. Не коммитить APK/AAR, ключи, полные bugreport и дампы аккаунта.
5. Перед каждым push получить отдельное подтверждение владельца; затем отправлять
   только рабочую ветку, например `git push origin HEAD:refs/heads/preweb-device-audio`.
6. Открыть PR **из `preweb-device-audio` в `interface-completion`**, описать
   исправления, оборудование, проверки и ограничения. Если база к тому времени
   интегрирована, владелец меняет target на `fork-base`. Мерж после проверки владельцем.

Для push в общий репозиторий нужен доступ Write. Если его нет, передать владельцу
GitHub username для приглашения; до этого можно работать в своём fork и прислать PR
с его ветки `preweb-device-audio` в `lemonke68/Larpgram:interface-completion`.
Наличие публичной ветки само по себе не выдаёт права записи.

Не включать в PR посторонние оптимизации, смену SDK, релизный номер или серверные
изменения. Новый USB/UAC стек согласовать отдельным объёмом. Если bit-perfect
невозможен на конкретной прошивке, зафиксировать доказанное ограничение.
