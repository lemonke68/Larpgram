// Клиент admin API Matrix Authentication Service.
//
// Ходим к MAS по докер-сети (http://matrix-authentication-service:8080/auth), наружу admin API
// закрыт в Traefik. Токен — client_credentials со scope urn:mas:admin; клиент заведён в конфиге
// MAS (clients + policy.data.admin_clients), см. DEPLOY.md.

const ADMIN_SCOPE = 'urn:mas:admin';

// Есть ли в scope сеанса это устройство (urn:matrix:client:device:ID, старый вид — с org.matrix.msc2967).
function scopeHasDevice(scope, deviceId) {
  return String(scope || '').split(' ').some((part) => part.endsWith(`:device:${deviceId}`));
}

export class MasError extends Error {
  constructor(status, body) {
    super(`MAS ответил ${status}: ${String(body).slice(0, 300)}`);
    this.status = status;
  }
}

export function createMas({
  baseUrl = process.env.MAS_URL || 'http://matrix-authentication-service:8080/auth',
  clientId = process.env.MAS_CLIENT_ID,
  clientSecret = process.env.MAS_CLIENT_SECRET,
  fetchImpl = fetch,
  now = Date.now,
} = {}) {
  const base = baseUrl.replace(/\/+$/, '');
  let token = null;
  let tokenExpiresAt = 0;

  async function accessToken() {
    if (token && now() < tokenExpiresAt) return token;
    if (!clientId || !clientSecret) throw new Error('MAS_CLIENT_ID / MAS_CLIENT_SECRET не заданы');
    const res = await fetchImpl(`${base}/oauth2/token`, {
      method: 'POST',
      headers: {
        authorization: `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`,
        'content-type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({ grant_type: 'client_credentials', scope: ADMIN_SCOPE }),
    });
    if (!res.ok) throw new MasError(res.status, await res.text());
    const json = await res.json();
    token = json.access_token;
    // Обновляем за минуту до истечения.
    tokenExpiresAt = now() + Math.max(30, (json.expires_in || 300) - 60) * 1000;
    return token;
  }

  async function call(method, path, body) {
    const send = async () =>
      fetchImpl(`${base}/api/admin/v1${path}`, {
        method,
        headers: {
          authorization: `Bearer ${await accessToken()}`,
          ...(body ? { 'content-type': 'application/json' } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
      });
    let res = await send();
    if (res.status === 401) {
      // Токен отозван или MAS перезапущен — берём новый один раз.
      token = null;
      res = await send();
    }
    return res;
  }

  const userOf = (json) => ({ id: json.data.id, username: json.data.attributes.username });

  return {
    /** Пользователь по нику или null. */
    async findUser(username) {
      const res = await call('GET', `/users/by-username/${encodeURIComponent(username)}`);
      if (res.status === 404) return null;
      if (!res.ok) throw new MasError(res.status, await res.text());
      return userOf(await res.json());
    },

    /** Пользователь по id MAS или null. */
    async getUser(userId) {
      const res = await call('GET', `/users/${encodeURIComponent(userId)}`);
      if (res.status === 404) return null;
      if (!res.ok) throw new MasError(res.status, await res.text());
      return userOf(await res.json());
    },

    /** Создаёт пользователя. null — ник занят или недопустим для сервера. */
    async createUser(username) {
      const res = await call('POST', '/users', { username });
      if (res.status === 409 || res.status === 400) return null;
      if (!res.ok) throw new MasError(res.status, await res.text());
      return userOf(await res.json());
    },

    /** false — MAS счёл пароль слишком слабым. */
    async setPassword(userId, password) {
      const res = await call('POST', `/users/${userId}/set-password`, {
        password,
        skip_password_check: false,
      });
      if (res.status === 400) return false;
      if (!res.ok) throw new MasError(res.status, await res.text());
      return true;
    },

    /** id пользователя, которому принадлежит адрес, или null. */
    async findUserIdByEmail(email) {
      const query = new URLSearchParams({ 'filter[email]': email, 'page[first]': '1' });
      const res = await call('GET', `/user-emails?${query}`);
      if (!res.ok) throw new MasError(res.status, await res.text());
      const first = (await res.json()).data?.[0];
      return first ? first.attributes.user_id : null;
    },

    /** Первый адрес пользователя или null. */
    async findEmailOfUser(userId) {
      const query = new URLSearchParams({ 'filter[user]': userId, 'page[first]': '1' });
      const res = await call('GET', `/user-emails?${query}`);
      if (!res.ok) throw new MasError(res.status, await res.text());
      const first = (await res.json()).data?.[0];
      return first ? first.attributes.email : null;
    },

    /**
     * Выпускает токен доступа для нового устройства пользователя (personal session MAS) и
     * возвращает его. Устройство задаём сами через scope — Synapse заведёт его при первом запросе.
     */
    async createDeviceSession(userId, deviceId, humanName) {
      const res = await call('POST', '/personal-sessions', {
        actor_user_id: userId,
        human_name: humanName,
        scope: `urn:matrix:client:api:* urn:matrix:client:device:${deviceId}`,
      });
      if (!res.ok) throw new MasError(res.status, await res.text());
      const token = (await res.json()).data?.attributes?.access_token;
      if (!token) throw new MasError(res.status, 'нет access_token в ответе');
      return token;
    },

    /** Удаляет все адреса пользователя (перед привязкой нового: адрес у аккаунта один). */
    async removeEmails(userId) {
      const query = new URLSearchParams({ 'filter[user]': userId, 'page[first]': '50' });
      const res = await call('GET', `/user-emails?${query}`);
      if (!res.ok) throw new MasError(res.status, await res.text());
      for (const item of (await res.json()).data || []) {
        const del = await call('DELETE', `/user-emails/${item.id}`);
        if (!del.ok && del.status !== 404) throw new MasError(del.status, await del.text());
      }
    },

    /**
     * Завершает сеанс устройства. Сеансы у MAS трёх видов (вход по паролю, вход через браузер,
     * привязка по QR) — ищем устройство во всех. true, если что-то завершили.
     */
    async endDeviceSession(userId, deviceId) {
      const kinds = [
        { path: 'compat-sessions', matches: (a) => a.device_id === deviceId, end: 'finish' },
        { path: 'oauth2-sessions', matches: (a) => scopeHasDevice(a.scope, deviceId), end: 'finish' },
        { path: 'personal-sessions', matches: (a) => scopeHasDevice(a.scope, deviceId), end: 'revoke', userFilter: 'filter[actor_user]' },
      ];
      let ended = false;
      for (const kind of kinds) {
        const query = new URLSearchParams({
          [kind.userFilter || 'filter[user]']: userId,
          'filter[status]': 'active',
          'page[first]': '100',
        });
        const res = await call('GET', `/${kind.path}?${query}`);
        if (!res.ok) throw new MasError(res.status, await res.text());
        for (const item of (await res.json()).data || []) {
          if (!kind.matches(item.attributes || {})) continue;
          const end = await call('POST', `/${kind.path}/${item.id}/${kind.end}`);
          if (!end.ok) throw new MasError(end.status, await end.text());
          ended = true;
        }
      }
      return ended;
    },

    /** false — адрес уже привязан к кому-то. */
    async addEmail(userId, email) {
      const res = await call('POST', '/user-emails', { user_id: userId, email });
      if (res.status === 409) return false;
      if (!res.ok) throw new MasError(res.status, await res.text());
      return true;
    },
  };
}
