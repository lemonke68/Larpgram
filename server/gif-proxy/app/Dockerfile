# Node 24 есть на сервере, берём тот же мажор, чтобы поведение fetch совпадало.
FROM node:24-alpine

# npm и corepack в образе не нужны совсем, а их собственные зависимости trivy находит
# уязвимыми.
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
    /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack

WORKDIR /app
COPY server.js ./

# Зависимостей нет намеренно: http и fetch встроены в Node, а чем меньше пакетов,
# тем меньше поводов обновлять образ из-за чужих уязвимостей.
USER node
EXPOSE 3000
CMD ["node", "server.js"]
