# Node 24 есть на сервере, берём тот же мажор, чтобы поведение fetch совпадало.
FROM node:24-alpine

WORKDIR /app
COPY server.js ./

# Зависимостей нет намеренно: http и fetch встроены в Node, а чем меньше пакетов,
# тем меньше поводов обновлять образ из-за чужих уязвимостей.
USER node
EXPOSE 3000
CMD ["node", "server.js"]
