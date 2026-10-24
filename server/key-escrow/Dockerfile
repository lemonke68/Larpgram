# better-sqlite3 — нативный модуль, поэтому не alpine (musl), а bookworm-slim с тулчейном
# для сборки. Собирается один раз при build, в рантайме тулчейн не нужен.
FROM node:24-bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Данные (SQLite) — в /data, владелец node, чтобы контейнер писал не из-под root.
RUN mkdir -p /data && chown -R node:node /data

COPY package.json package-lock.json ./
# npm и corepack нужны только для сборки, а их собственные зависимости trivy находит
# уязвимыми. В рантайме приложение запускается через node, без npm.
RUN npm ci --omit=dev \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
       /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack

COPY . .

ENV NODE_ENV=production
ENV DB_PATH=/data/escrow.db
EXPOSE 8080
USER node

CMD ["node", "index.js"]
