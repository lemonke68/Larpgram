// Маршруты сервиса. Зависимости (MAS, почта, база, часы) приходят снаружи, чтобы тесты гоняли
// приложение целиком без сети.

import express from 'express';
import {
  hashCode, genCode, genTicket, timingSafeEqualHex, genPairCode, hashPairCode, genDeviceId,
} from './crypto.js';
import { createLimiter } from './limiter.js';
import {
  normalizeUsername, usernameProblem, normalizeEmail, isValidEmail, passwordProblem, parseLogin,
  judgeCode, localpartOf, cleanDeviceName, maskEmail,
} from './rules.js';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;

export function createApp({ mas, mailer, db, matrix, now = Date.now, config = {} }) {
  const codeTtlMs = (config.codeTtlSeconds ?? 600) * 1000;
  const resendMs = (config.codeResendSeconds ?? 60) * 1000;
  const maxAttempts = config.maxAttempts ?? 5;
  // После верного кода заявка живёт ещё столько: время исправить пароль, который MAS не принял.
  const verifiedTtlMs = 15 * MINUTE;
  const startsPerIpHour = config.startsPerIpHour ?? 10;
  const checksPerIpHour = config.checksPerIpHour ?? 40;
  const mailsPerAddressHour = config.mailsPerAddressHour ?? 5;
  const pairTtlMs = (config.pairTtlSeconds ?? 120) * 1000;
  const offersPerUserHour = config.offersPerUserHour ?? 30;
  const loginsPerIpHour = config.loginsPerIpHour ?? 30;
  const loginsPerUserHour = config.loginsPerUserHour ?? 10;

  const limiter = createLimiter({ now });
  const app = express();
  app.disable('x-powered-by');
  // За Traefik: адрес клиента — последний в X-Forwarded-For.
  app.set('trust proxy', 1);
  app.use(express.json({ limit: '16kb' }));

  // Журнал: что сделали и с каким ником. Кодов, паролей и адресов почты тут нет.
  function audit(action, extra = {}) {
    console.log(JSON.stringify({ audit: action, at: new Date(now()).toISOString(), ...extra }));
  }

  const fail = (res, status, error, extra = {}) => res.status(status).json({ error, ...extra });

  // Обёртка: падение MAS или базы — 502, а не висящий запрос.
  const route = (handler) => async (req, res) => {
    try {
      await handler(req, res);
    } catch (e) {
      console.error(`${req.path} упал:`, e.message);
      if (!res.headersSent) fail(res, 502, 'upstream_failed');
    }
  };

  function newTicket(kind, { username = '', email = '', userId = '' }) {
    const id = genTicket();
    const code = genCode();
    db.insert.run({
      id, kind, username, email, user_id: userId,
      code_hash: hashCode(code), expires_at: now() + codeTtlMs, last_sent_at: now(),
    });
    return { id, code };
  }

  /**
   * Проверяет код заявки. Возвращает строку заявки, если код принят (сейчас или раньше), иначе
   * сам отвечает клиенту и возвращает null.
   */
  function redeem(req, res, kind) {
    if (!limiter.allow(`check:${req.ip}`, checksPerIpHour, HOUR)) {
      fail(res, 429, 'too_many_requests');
      return null;
    }
    const row = db.get.get(String(req.body?.ticket || ''));
    if (!row || row.kind !== kind) {
      fail(res, 410, 'code_expired');
      return null;
    }
    if (row.verified) {
      if (now() <= row.expires_at) return row;
      db.delete.run(row.id);
      fail(res, 410, 'code_expired');
      return null;
    }
    const code = String(req.body?.code || '').trim();
    const codeMatches = /^\d{6}$/.test(code) && timingSafeEqualHex(row.code_hash, hashCode(code));
    switch (judgeCode({ row, codeMatches, now: now(), maxAttempts })) {
      case 'ok':
        db.markVerified.run(now() + verifiedTtlMs, row.id);
        return { ...row, verified: 1 };
      case 'expired':
        db.delete.run(row.id);
        fail(res, 410, 'code_expired');
        return null;
      case 'too_many':
        db.delete.run(row.id);
        fail(res, 429, 'too_many_attempts');
        return null;
      default: {
        db.bumpAttempts.run(row.id);
        const left = maxAttempts - (row.attempts + 1);
        if (left <= 0) {
          db.delete.run(row.id);
          fail(res, 429, 'too_many_attempts');
        } else {
          fail(res, 400, 'wrong_code', { attempts_left: left });
        }
        return null;
      }
    }
  }

  app.get('/health', (_req, res) => res.json({ ok: true }));

  // Шаг 1 регистрации: ник и почта свободны — шлём код.
  app.post('/register/start', route(async (req, res) => {
    if (!limiter.allow(`start:${req.ip}`, startsPerIpHour, HOUR)) return fail(res, 429, 'too_many_requests');

    const username = normalizeUsername(req.body?.username);
    const email = normalizeEmail(req.body?.email);
    const problem = usernameProblem(username);
    if (problem) return fail(res, problem === 'username_taken' ? 409 : 400, problem);
    if (!isValidEmail(email)) return fail(res, 400, 'email_invalid');

    if (await mas.findUser(username)) return fail(res, 409, 'username_taken');
    if (await mas.findUserIdByEmail(email)) return fail(res, 409, 'email_taken');

    if (!limiter.allow(`mail:${email}`, mailsPerAddressHour, HOUR)) return fail(res, 429, 'too_many_requests');

    const { id, code } = newTicket('register', { username, email });
    try {
      await mailer.sendCode('register', email, code);
    } catch (e) {
      console.error('отправка письма упала:', e.message);
      db.delete.run(id);
      return fail(res, 502, 'mail_failed');
    }
    audit('register-start', { username });
    res.json({ ticket: id, resend_after: resendMs / 1000 });
  }));

  // Шаг 2 регистрации: код верный — создаём аккаунт, привязываем почту, ставим пароль.
  app.post('/register/confirm', route(async (req, res) => {
    const password = req.body?.password;
    const weak = passwordProblem(password);
    if (weak) return fail(res, 400, weak);

    const row = redeem(req, res, 'register');
    if (!row) return;

    let userId = row.user_id;
    if (!userId) {
      const user = await mas.createUser(row.username);
      if (!user) {
        // Ник заняли, пока человек вводил код.
        db.delete.run(row.id);
        return fail(res, 409, 'username_taken');
      }
      userId = user.id;
      db.setUserId.run(userId, row.id);
      if (!(await mas.addEmail(userId, row.email))) {
        // Почту заняли в те же минуты. Аккаунт без пароля остаётся пустышкой — в журнал.
        audit('register-orphan', { username: row.username });
        db.delete.run(row.id);
        return fail(res, 409, 'email_taken');
      }
    }

    // Отказ MAS по стойкости: заявка остаётся, клиент повторяет с другим паролем без кода.
    if (!(await mas.setPassword(userId, password))) return fail(res, 400, 'password_weak');

    db.delete.run(row.id);
    audit('register-done', { username: row.username });
    res.json({ username: row.username });
  }));

  // Вход по почте: слой совместимости MAS принимает только ник, поэтому приложение сначала
  // спрашивает ник по адресу. Это выдаёт, что адрес зарегистрирован (как и регистрация с её
  // «почта занята»), поэтому лимит по IP общий с заявками.
  app.post('/login/resolve', route(async (req, res) => {
    if (!limiter.allow(`resolve:${req.ip}`, checksPerIpHour, HOUR)) return fail(res, 429, 'too_many_requests');
    const email = normalizeEmail(req.body?.email);
    const userId = isValidEmail(email) ? await mas.findUserIdByEmail(email) : null;
    const user = userId ? await mas.getUser(userId) : null;
    if (!user) return fail(res, 404, 'not_found');
    res.json({ username: user.username });
  }));

  // Вход с подтверждением по почте, шаг 1: пароль верный и у аккаунта есть почта — шлём код.
  // Сессию выдаёт не сервис: после кода приложение входит обычным `/login`. Поэтому это защита на
  // стороне приложения — сторонний Matrix-клиент по-прежнему входит одним паролем.
  app.post('/login/start', route(async (req, res) => {
    if (!limiter.allow(`login:${req.ip}`, loginsPerIpHour, HOUR)) return fail(res, 429, 'too_many_requests');
    const login = parseLogin(req.body?.login);
    const password = typeof req.body?.password === 'string' ? req.body.password : '';
    if (!(login.email || login.username) || !password) return fail(res, 400, 'login_required');

    let user = null;
    if (login.email) {
      const userId = isValidEmail(login.email) ? await mas.findUserIdByEmail(login.email) : null;
      user = userId ? await mas.getUser(userId) : null;
    } else {
      user = await mas.findUser(login.username);
    }
    // Нет аккаунта и неверный пароль — один ответ: по ручке нельзя перебирать ники и адреса.
    if (!user) return fail(res, 403, 'invalid_credentials');
    if (!limiter.allow(`login-user:${user.username}`, loginsPerUserHour, HOUR)) return fail(res, 429, 'too_many_requests');
    if (!(await matrix.checkPassword(user.username, password))) return fail(res, 403, 'invalid_credentials');

    const email = await mas.findEmailOfUser(user.id);
    // Старые аккаунты без почты входят как раньше: код слать некуда.
    if (!email) return res.json({ code_required: false, username: user.username });
    if (!limiter.allow(`mail:${email}`, mailsPerAddressHour, HOUR)) return fail(res, 429, 'too_many_requests');

    const { id, code } = newTicket('login', { username: user.username, email, userId: user.id });
    try {
      await mailer.sendCode('login', email, code);
    } catch (e) {
      console.error('отправка письма упала:', e.message);
      db.delete.run(id);
      return fail(res, 502, 'mail_failed');
    }
    audit('login-start', { username: user.username });
    res.json({
      code_required: true, ticket: id, resend_after: resendMs / 1000,
      username: user.username, email_hint: maskEmail(email),
    });
  }));

  // Шаг 2: код верный — приложению можно входить.
  app.post('/login/confirm', route(async (req, res) => {
    const row = redeem(req, res, 'login');
    if (!row) return;
    db.delete.run(row.id);
    audit('login-done', { username: row.username });
    res.json({ username: row.username });
  }));

  // Сброс пароля, шаг 1. Ответ одинаковый, есть аккаунт с почтой или нет: иначе по этой ручке
  // можно было бы перебирать ники и адреса.
  app.post('/password/forgot', route(async (req, res) => {
    if (!limiter.allow(`start:${req.ip}`, startsPerIpHour, HOUR)) return fail(res, 429, 'too_many_requests');

    const login = parseLogin(req.body?.login);
    const key = login.email || login.username;
    if (!key) return fail(res, 400, 'login_required');
    if (!limiter.allow(`mail:${key}`, mailsPerAddressHour, HOUR)) return fail(res, 429, 'too_many_requests');

    let target = null; // { userId, username, email }
    if (login.email) {
      const userId = isValidEmail(login.email) ? await mas.findUserIdByEmail(login.email) : null;
      const user = userId ? await mas.getUser(userId) : null;
      if (user) target = { userId, username: user.username, email: login.email };
    } else {
      const user = await mas.findUser(login.username);
      const email = user ? await mas.findEmailOfUser(user.id) : null;
      if (email) target = { userId: user.id, username: user.username, email };
    }

    const { id, code } = newTicket('reset', target || {});
    if (target) {
      // Не ждём доставки: время ответа не должно выдавать, ушло ли письмо.
      mailer.sendCode('reset', target.email, code)
        .catch((e) => console.error('отправка письма упала:', e.message));
      audit('reset-start', { username: target.username });
    }
    res.json({ ticket: id, resend_after: resendMs / 1000 });
  }));

  // Сброс пароля, шаг 2.
  app.post('/password/reset', route(async (req, res) => {
    const password = req.body?.password;
    const weak = passwordProblem(password);
    if (weak) return fail(res, 400, weak);

    const row = redeem(req, res, 'reset');
    if (!row) return;

    if (!(await mas.setPassword(row.user_id, password))) return fail(res, 400, 'password_weak');

    db.delete.run(row.id);
    audit('reset-done', { username: row.username });
    res.json({ username: row.username });
  }));

  // Только проверить код, не меняя аккаунт: экран нового пароля показываем после верного кода.
  app.post('/code/check', route(async (req, res) => {
    const kind = db.get.get(String(req.body?.ticket || ''))?.kind;
    if (!redeem(req, res, kind || 'reset')) return;
    res.sendStatus(204);
  }));

  // Прислать код ещё раз по той же заявке.
  app.post('/code/resend', route(async (req, res) => {
    const row = db.get.get(String(req.body?.ticket || ''));
    if (!row || row.verified || now() > row.expires_at) return fail(res, 410, 'code_expired');
    if (now() - row.last_sent_at < resendMs) {
      return fail(res, 429, 'too_soon', { retry_after: Math.ceil((resendMs - (now() - row.last_sent_at)) / 1000) });
    }
    const limitKey = row.email || `ticket:${row.id}`;
    if (!limiter.allow(`mail:${limitKey}`, mailsPerAddressHour, HOUR)) return fail(res, 429, 'too_many_requests');

    const code = genCode();
    db.newCode.run(hashCode(code), now() + codeTtlMs, now(), row.id);
    if (row.email) {
      if (row.kind !== 'reset') {
        try {
          await mailer.sendCode(row.kind, row.email, code);
        } catch (e) {
          console.error('отправка письма упала:', e.message);
          return fail(res, 502, 'mail_failed');
        }
      } else {
        mailer.sendCode('reset', row.email, code)
          .catch((e) => console.error('отправка письма упала:', e.message));
      }
    }
    res.json({ resend_after: resendMs / 1000 });
  }));

  // --- Привязка устройства по QR ---
  //
  // Вошедшее устройство просит код и показывает его QR-кодом; новое сканирует и меняет код на
  // сессию того же аккаунта. Код одноразовый и живёт две минуты: показать QR — значит впустить.

  // Мидлварь: Bearer с токеном Matrix -> user_id. Токен в лог не пишем.
  const auth = (handler) => route(async (req, res) => {
    const header = req.get('authorization') || '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
    if (!token) return fail(res, 401, 'unauthorized');
    const who = await matrix.whoami(token);
    if (!who) return fail(res, 401, 'unauthorized');
    req.userId = who.userId;
    await handler(req, res);
  });

  // Создать предложение. Отвечает кодом для QR и сроком жизни.
  app.post('/pair/offer', auth(async (req, res) => {
    if (!limiter.allow(`offer:${req.userId}`, offersPerUserHour, HOUR)) return fail(res, 429, 'too_many_requests');
    const code = genPairCode();
    db.insertOffer.run(hashPairCode(code), req.userId, now() + pairTtlMs);
    audit('pair-offer', { user: req.userId });
    res.set('Cache-Control', 'no-store');
    res.json({ code, expires_in: pairTtlMs / 1000 });
  }));

  // Спросить, вошло ли новое устройство по этому коду (вошедшее устройство опрашивает раз в пару секунд).
  app.post('/pair/status', auth(async (req, res) => {
    const offer = db.getOffer.get(hashPairCode(String(req.body?.code || '')));
    if (!offer || offer.user_id !== req.userId) return fail(res, 404, 'not_found');
    if (offer.redeemed_at > 0) return res.json({ state: 'redeemed' });
    res.json({ state: now() > offer.expires_at ? 'expired' : 'waiting' });
  }));

  // Новое устройство меняет код из QR на сессию. Без авторизации: её ещё нет.
  app.post('/pair/redeem', route(async (req, res) => {
    if (!limiter.allow(`check:${req.ip}`, checksPerIpHour, HOUR)) return fail(res, 429, 'too_many_requests');
    const codeHash = hashPairCode(String(req.body?.code || ''));
    const offer = db.getOffer.get(codeHash);
    if (!offer || offer.redeemed_at > 0 || now() > offer.expires_at) return fail(res, 410, 'code_expired');

    const username = localpartOf(offer.user_id);
    const user = username ? await mas.findUser(username) : null;
    if (!user) return fail(res, 410, 'code_expired');

    // Сначала гасим код, потом выпускаем токен: при гонке двух телефонов сессию получит один.
    if (db.redeemOffer.run(now(), codeHash, now()).changes !== 1) return fail(res, 410, 'code_expired');

    const deviceId = genDeviceId();
    const deviceName = cleanDeviceName(req.body?.device_name);
    const accessToken = await mas.createDeviceSession(user.id, deviceId, deviceName);
    await matrix.setDeviceName(accessToken, deviceId, deviceName);
    audit('pair-redeem', { user: offer.user_id, device: deviceId });
    res.set('Cache-Control', 'no-store');
    res.json({
      user_id: offer.user_id,
      device_id: deviceId,
      access_token: accessToken,
      homeserver_url: matrix.homeserverUrl,
    });
  }));

  // --- Аккаунт вошедшего пользователя (настройки приложения вместо страницы MAS) ---

  async function masUserOf(req) {
    const username = localpartOf(req.userId);
    return username ? mas.findUser(username) : null;
  }

  // Привязать или сменить почту, шаг 1: код на новый адрес. Пароль не спрашиваем: человек уже вошёл.
  app.post('/email/start', auth(async (req, res) => {
    if (!limiter.allow(`start:${req.ip}`, startsPerIpHour, HOUR)) return fail(res, 429, 'too_many_requests');
    const email = normalizeEmail(req.body?.email);
    if (!isValidEmail(email)) return fail(res, 400, 'email_invalid');
    const user = await masUserOf(req);
    if (!user) return fail(res, 401, 'unauthorized');
    const owner = await mas.findUserIdByEmail(email);
    if (owner && owner !== user.id) return fail(res, 409, 'email_taken');
    if (!limiter.allow(`mail:${email}`, mailsPerAddressHour, HOUR)) return fail(res, 429, 'too_many_requests');

    const { id, code } = newTicket('email', { username: user.username, email, userId: user.id });
    try {
      await mailer.sendCode('email', email, code);
    } catch (e) {
      console.error('отправка письма упала:', e.message);
      db.delete.run(id);
      return fail(res, 502, 'mail_failed');
    }
    audit('email-start', { username: user.username });
    res.json({ ticket: id, resend_after: resendMs / 1000 });
  }));

  // Шаг 2: код верный — адрес становится почтой аккаунта (прежний снимается).
  app.post('/email/confirm', auth(async (req, res) => {
    const ticket = db.get.get(String(req.body?.ticket || ''));
    // Чужую заявку не трогаем даже с верным кодом.
    if (ticket && ticket.username !== localpartOf(req.userId)) return fail(res, 410, 'code_expired');
    const row = redeem(req, res, 'email');
    if (!row) return;
    await mas.removeEmails(row.user_id);
    if (!(await mas.addEmail(row.user_id, row.email))) {
      db.delete.run(row.id);
      return fail(res, 409, 'email_taken');
    }
    db.delete.run(row.id);
    audit('email-done', { username: row.username });
    res.json({ email: row.email });
  }));

  // Завершить сеанс другого устройства этого аккаунта.
  app.post('/sessions/end', auth(async (req, res) => {
    const deviceId = String(req.body?.device_id || '');
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(deviceId)) return fail(res, 400, 'device_invalid');
    const user = await masUserOf(req);
    if (!user) return fail(res, 401, 'unauthorized');
    if (!(await mas.endDeviceSession(user.id, deviceId))) return fail(res, 404, 'not_found');
    audit('session-end', { username: user.username, device: deviceId });
    res.sendStatus(204);
  }));

  // Уборка: просроченные заявки и старые счётчики.
  const sweep = () => {
    db.deleteExpired.run(now());
    // Погашенные предложения держим до конца срока, чтобы вошедшее устройство узнало об успехе.
    db.deleteExpiredOffers.run(now() - HOUR);
    limiter.sweep(HOUR);
  };

  return { app, sweep };
}
