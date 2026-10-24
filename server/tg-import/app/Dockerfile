FROM node:24-bookworm-slim

# ffmpeg кодирует анимированный WebP и декодирует видео-стикеры, python нужен только
# ради rlottie: другого способа отрисовать Lottie из .tgs нет.
# Проверено, что в ffmpeg этой сборки есть и декодер libvpx-vp9, и кодер libwebp_anim.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg python3 python3-venv \
    && rm -rf /var/lib/apt/lists/*

# Отдельное окружение: системный python в Debian закрыт от pip (PEP 668).
# pip и setuptools после установки убираем: в рантайме они не нужны, а их вложенные
# зависимости (urllib3, msgpack, старый setuptools) trivy находит уязвимыми.
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir --disable-pip-version-check \
       rlottie-python pillow \
    && /opt/venv/bin/pip uninstall -y --disable-pip-version-check pip setuptools
ENV PATH="/opt/venv/bin:$PATH"

WORKDIR /app
COPY package.json ./
# undici нужен ради ProxyAgent: встроенный fetch в Node не умеет ходить через прокси.
# npm и corepack нужны только для сборки, а их собственные зависимости trivy находит
# уязвимыми. В рантайме приложение запускается через node, без npm.
RUN npm install --omit=dev --no-audit --no-fund \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
       /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack
COPY server.js convert.py ./

# Кэш сконвертированных стикеров. Содержимое одноразовое: потеряется — пересчитается.
ENV CACHE_DIR=/cache
RUN mkdir -p /cache/tmp && chown -R node:node /cache

USER node
EXPOSE 3000
CMD ["node", "server.js"]
