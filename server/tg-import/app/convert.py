#!/usr/bin/env python3
"""Конвертация анимированных стикеров Telegram в анимированный WebP.

Вызывается из server.js как `convert.py <вход> <выход>`, формат определяется по
расширению входа. На выходе всегда анимированный WebP: Coil в Element X умеет его из
коробки (`AnimatedImageDecoder` уже включён в апстриме), поэтому клиент не трогаем.

Кодируем через Pillow, а не через ffmpeg, ровно ради одной ручки: alpha_quality.
У ffmpeg её нет, а именно она решает вес — libwebp по умолчанию жмёт альфу без потерь,
и на стикерах это ШЕСТЬДЕСЯТ процентов файла (1470 КБ против 604 КБ без альфы вовсе).

Параметры подобраны замерами на настоящих паках 2026-08-09 (34 стикера HotCherry,
медиана веса):
  384 px    в чате стикер рисуется 128 dp, в пикере 80 dp: на экране 3x нужно ровно
            384, а 512 даёт +33% веса и ни одного лишнего пикселя;
  24 fps    30 -> 24 срезает пятую часть веса, на глаз незаметно;
  q 70      качество цвета почти не влияет на вес (q70 -> q50 это 9%), снижать незачем;
  aq 60     альфа: 1002 -> 679 КБ. Проверено увеличением втрое — края не сыплются даже
            при 30, шестьдесят взято с запасом;
  method 4  ГЛАВНОЕ: method 6 кодирует в сто раз дольше (48 с против 0.5 с) ради 2%
            размера. Не трогать, не разобравшись.

Итог: медиана 543 КБ, пак из 34 штук 17.4 МБ. С дефолтными настройками было 31.8 МБ.

Всё перекрывается переменными окружения, чтобы подбирать параметры без пересборки
образа. Если меняете их насовсем — поднимите CONV_VERSION в server.js, иначе в кэше
останутся файлы, посчитанные по-старому.
"""
import gzip
import json
import os
import subprocess
import sys

MAX_SIDE = int(os.environ.get("STICKER_MAX_SIDE", 384))
FPS = int(os.environ.get("STICKER_FPS", 24))
QUALITY = int(os.environ.get("STICKER_QUALITY", 70))
ALPHA_QUALITY = int(os.environ.get("STICKER_ALPHA_QUALITY", 60))
METHOD = int(os.environ.get("STICKER_METHOD", 4))
# У Telegram стикер не бывает длиннее трёх секунд, но проверяем и мы: битый файл с
# гигантской длительностью не должен занять ядро надолго.
MAX_SECONDS = float(os.environ.get("STICKER_MAX_SECONDS", 3.0))


def fit(width, height):
    """Вписываем в квадрат MAX_SIDE, не растягивая: стикеры бывают неквадратные."""
    if not width or not height or width <= 0 or height <= 0:
        return MAX_SIDE, MAX_SIDE
    scale = min(MAX_SIDE / width, MAX_SIDE / height, 1.0)
    w = max(2, int(round(width * scale / 2)) * 2)
    h = max(2, int(round(height * scale / 2)) * 2)
    return w, h


def save_webp(frames, dst):
    """Общий выход для обоих форматов."""
    if not frames:
        raise RuntimeError("не получилось ни одного кадра")
    frames[0].save(
        dst,
        format="WEBP",
        save_all=True,
        append_images=frames[1:],
        duration=max(1, round(1000 / FPS)),
        loop=0,
        quality=QUALITY,
        alpha_quality=ALPHA_QUALITY,
        method=METHOD,
        # Даёт всего процентов пять (в стикерах слишком много движения, чтобы хранить
        # разницу между кадрами), но достаётся почти даром.
        minimize_size=True,
    )


def probe_size(src):
    """Размер кадра видео-стикера."""
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0",
         "-show_entries", "stream=width,height", "-of", "json", src],
        check=True, capture_output=True,
    ).stdout
    stream = json.loads(out)["streams"][0]
    return stream.get("width"), stream.get("height")


def convert_webm(src, dst):
    """Видео-стикер: ffmpeg только декодирует, кодирует Pillow.

    Декодер задан ЯВНО, и это не украшательство: прозрачность у видео-стикеров лежит в
    BlockAdditions матрёшки, родной vp9-декодер ffmpeg её молча теряет, и стикер
    приезжает непрозрачным прямоугольником. Альфу видит только libvpx-vp9.
    """
    from PIL import Image

    width, height = fit(*probe_size(src))
    raw = subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin",
         "-c:v", "libvpx-vp9", "-i", src,
         "-t", str(MAX_SECONDS),
         "-vf", f"fps={FPS},scale={width}:{height}:flags=lanczos",
         "-f", "rawvideo", "-pix_fmt", "rgba", "-"],
        check=True, capture_output=True,
    ).stdout

    stride = width * height * 4
    frames = [
        Image.frombytes("RGBA", (width, height), raw[i:i + stride])
        for i in range(0, len(raw) - stride + 1, stride)
    ]
    save_webp(frames, dst)


def convert_tgs(src, dst):
    """Анимированный стикер: gzip -> Lottie JSON -> rlottie -> кадры -> Pillow.

    Сам рендер дешёвый: 46 кадров занимают 0.05 с, всё время съедает кодирование.
    """
    from rlottie_python import LottieAnimation

    with gzip.open(src) as fh:
        meta = json.load(fh)
    width, height = fit(meta.get("w"), meta.get("h"))

    anim = LottieAnimation.from_tgs(src)
    total = anim.lottie_animation_get_totalframe()
    src_fps = anim.lottie_animation_get_framerate() or 60
    duration = min(total / src_fps, MAX_SECONDS)
    want = max(1, round(duration * FPS))

    frames = []
    for i in range(want):
        # Кадр исходной анимации, ближайший к нужному моменту времени.
        src_i = min(total - 1, round(i * total / want))
        frames.append(
            anim.render_pillow_frame(frame_num=src_i, width=width, height=height).convert("RGBA")
        )
    save_webp(frames, dst)


def main():
    if len(sys.argv) != 3:
        raise SystemExit("использование: convert.py <вход> <выход>")
    src, dst = sys.argv[1], sys.argv[2]
    ext = os.path.splitext(src)[1].lower()
    if ext == ".tgs":
        convert_tgs(src, dst)
    elif ext == ".webm":
        convert_webm(src, dst)
    else:
        raise SystemExit(f"нечего конвертировать: {ext}")


if __name__ == "__main__":
    main()
