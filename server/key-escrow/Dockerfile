# better-sqlite3 — нативный модуль, поэтому не alpine (musl), а bookworm-slim с тулчейном
# для сборки. Собирается один раз при build, в рантайме тулчейн не нужен.
FROM node:20-bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Данные (SQLite) — в /data, владелец node, чтобы контейнер писал не из-под root.
RUN mkdir -p /data && chown -R node:node /data

COPY package.json ./
RUN npm install --omit=dev

COPY . .

ENV NODE_ENV=production
ENV DB_PATH=/data/escrow.db
EXPOSE 8080
USER node

CMD ["node", "index.js"]
