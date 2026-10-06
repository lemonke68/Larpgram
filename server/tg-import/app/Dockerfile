FROM node:24-bookworm-slim

# ffmpeg кодирует анимированный WebP и декодирует видео-стикеры, python нужен только
# ради rlottie: другого способа отрисовать Lottie из .tgs нет.
# Проверено, что в ffmpeg этой сборки есть и декодер libvpx-vp9, и кодер libwebp_anim.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg python3 python3-venv \
    && rm -rf /var/lib/apt/lists/*

# Отдельное окружение: системный python в Debian закрыт от pip (PEP 668).
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir --disable-pip-version-check \
       rlottie-python pillow
ENV PATH="/opt/venv/bin:$PATH"

WORKDIR /app
COPY package.json ./
# undici нужен ради ProxyAgent: встроенный fetch в Node не умеет ходить через прокси.
RUN npm install --omit=dev --no-audit --no-fund
COPY server.js convert.py ./

# Кэш сконвертированных стикеров. Содержимое одноразовое: потеряется — пересчитается.
ENV CACHE_DIR=/cache
RUN mkdir -p /cache/tmp && chown -R node:node /cache

USER node
EXPOSE 3000
CMD ["node", "server.js"]
