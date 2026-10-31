# Замеры на Honor

Стенд: MAR-LX1M, Android 10, 1080×2312, пакет `ru.mangokokos.larpgram.perf`.
Сборка использует R8 и `profileable`, устанавливается рядом с основным приложением.
JDK 21; одна сборка одновременно:

```sh
./gradlew --max-workers=3 :app:assembleGplayPerf
```

`run.sh start|list|folders|chat|timeline 3` запускает отдельный сценарий. `suite.sh` ожидает
открытый список чатов, две папки, «Избранное» первой строкой и тестовый `test-channel`.
Часть координат в suite.sh рассчитана на этот экран Honor; для другого устройства их
нужно адаптировать. Ввод не отправляется, но перед тестом у тестового чата не должно быть
чужого черновика. Аккаунт и папки между сравниваемыми прогонами сохраняются.

```sh
RUNS=3 tools/larpgram/perf/suite.sh
SECS=60 tools/larpgram/perf/background.sh
```

## Состояние компиляции

После переустановки APK прогрев начинается заново. Сравнивать давно установленный APK с
только что обновлённым без контроля компиляции нельзя. Для сравнения изменений кода отдельно
от JIT обе сборки измеряются с одинаковой полной AOT:

```sh
adb -s SBXDU19428000351 shell cmd package compile -m speed -f ru.mangokokos.larpgram.perf
```

Это контрольный режим, а не обещание таких же цифр на свежей пользовательской установке.
Первые открытия и повторные открытия записываются отдельно.

В `app/src/main/baseline-prof.txt` — вручную отобранные пути приложения по замерам. Они
дополняют профили AndroidX. Проверять нужно не только наличие `assets/dexopt/baseline.prof`
в APK, но и фактическую компиляцию на устройстве. Для отдельной проверки поставляемого
профиля AGP создаёт metadata рядом с APK:

```sh
adb -s SBXDU19428000351 install-multiple -r \
  app/build/outputs/apk/gplay/perf/app-gplay-arm64-v8a-perf.apk \
  app/build/outputs/apk/gplay/perf/baselineProfiles/1/app-gplay-arm64-v8a-perf.dm
adb -s SBXDU19428000351 shell dumpsys package ru.mangokokos.larpgram.perf
```

В dexopt ожидается `speed-profile` с причиной `install-dm`. Обычный скачанный APK и установка
с metadata — разные условия. Профиль в APK сам по себе не доказывает немедленную AOT на
первом запуске. [Методика Android](https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure).

## Трассы и интерпретация

На EMUI сообщения самой утилиты могут попасть в stdout и испортить бинарную трассу:

```sh
adb -s SBXDU19428000351 exec-out \
  'perfetto -o - -t 12s --app ru.mangokokos.larpgram.perf sched freq gfx view input am wm dalvik binder_driver 2>/dev/null' \
  > chat.perfetto-trace
```

CPU-выборка: `/data/local/tmp/simpleperf record --app ru.mangokokos.larpgram.perf -g --duration 22`.
Сценарий запускается, пока запись активна. Для анализа сохраняется mapping именно этого APK;
одного имени функции после оптимизаций R8 недостаточно для вывода о выполняемой возможности.

`gfx.py` использует гистограмму окна: EMUI хранит слишком мало строк PROFILEDATA.
Процентили и опоздание являются оценкой по корзинам, а не точными длительностями отдельных
кадров. Основные сравнения — одинаковый сценарий, число тяжёлых кадров и суммарное опоздание.
Нулевые кадры при активном сценарии требуют проверки навигации; в покое ноль кадров допустим.
