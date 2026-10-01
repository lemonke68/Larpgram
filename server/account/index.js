// Larpgram account: регистрация и сброс пароля из приложения, без браузерных форм MAS.
//
// Клиент (libraries/accountapi) ходит на push.mango-kokos.ru/account, Traefik срезает префикс
// /account. Ручки открыты без авторизации (аккаунта у человека ещё нет или пароль забыт), поэтому
// защита — код с почты, лимиты по IP и адресу, одинаковые ответы на сбросе. Сами аккаунты и пароли
// живут в MAS, сервис обращается к его admin API (lib/mas.js). Вход после регистрации приложение
// делает само обычным Matrix-логином.

import { createApp } from './lib/app.js';
import { createMas } from './lib/mas.js';
import { createMailer } from './lib/mail.js';
import { createMatrix } from './lib/matrix.js';
import { openDb } from './lib/db.js';
import { hashCode } from './lib/crypto.js';

const PORT = Number(process.env.PORT || 8080);

// Падаем на старте, если секреты не заданы: лучше не подняться, чем работать наполовину.
hashCode('warmup');
if (!process.env.MAS_CLIENT_ID || !process.env.MAS_CLIENT_SECRET) {
  throw new Error('MAS_CLIENT_ID / MAS_CLIENT_SECRET не заданы');
}

const { app, sweep } = createApp({
  mas: createMas(),
  mailer: createMailer(),
  matrix: createMatrix(),
  db: openDb(),
  config: {
    codeTtlSeconds: Number(process.env.CODE_TTL_SECONDS || 600),
    codeResendSeconds: Number(process.env.CODE_RESEND_SECONDS || 60),
    maxAttempts: Number(process.env.MAX_ATTEMPTS || 5),
  },
});

setInterval(sweep, 10 * 60_000).unref();

app.listen(PORT, () => console.log(`account слушает :${PORT}`));
