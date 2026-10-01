// Проверка токена клиента: сами его не разбираем, спрашиваем сервер. whoami возвращает user_id,
// если токен живой. Нужна ручкам, которые вызывает уже вошедшее устройство (привязка по QR).

export function createMatrix({
  homeserverUrl = process.env.HOMESERVER_URL || 'https://matrix.mango-kokos.ru',
  fetchImpl = fetch,
} = {}) {
  const base = homeserverUrl.replace(/\/+$/, '');
  return {
    homeserverUrl: base,

    /**
     * { userId, deviceId } или null, если токен не годится (401/403). Недоступность сервера, 429 и
     * 5xx — исключение: вызывающий отвечает 502, а не «плохой токен».
     */
    async whoami(token) {
      const res = await fetchImpl(`${base}/_matrix/client/v3/account/whoami`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.status === 401 || res.status === 403) return null;
      if (!res.ok) throw new Error(`whoami HTTP ${res.status}`);
      const body = await res.json();
      return body?.user_id ? { userId: body.user_id, deviceId: body.device_id || null } : null;
    },

    /**
     * Верен ли пароль: пробный вход через `/login` и сразу выход. Отдельной проверки пароля у MAS
     * нет. true/false; лимит запросов, 5xx и недоступность сервера — исключение.
     */
    async checkPassword(username, password) {
      const res = await fetchImpl(`${base}/_matrix/client/v3/login`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          type: 'm.login.password',
          identifier: { type: 'm.id.user', user: username },
          password,
          initial_device_display_name: 'Larpgram: проверка пароля',
        }),
      });
      if (res.status === 400 || res.status === 401 || res.status === 403) return false;
      if (!res.ok) throw new Error(`login HTTP ${res.status}`);
      const token = (await res.json())?.access_token;
      if (token) {
        // Пробная сессия не должна остаться в списке устройств.
        try {
          await fetchImpl(`${base}/_matrix/client/v3/logout`, {
            method: 'POST',
            headers: { Authorization: `Bearer ${token}` },
          });
        } catch (e) {
          console.error('выход из пробной сессии упал:', e.message);
        }
      }
      return Boolean(token);
    },

    /**
     * Называет устройство в списке сеансов. Токен привязки заводит устройство без имени, поэтому
     * подписываем его сами — токеном самого устройства. Не вышло — не беда, вход важнее.
     */
    async setDeviceName(token, deviceId, name) {
      try {
        const res = await fetchImpl(`${base}/_matrix/client/v3/devices/${encodeURIComponent(deviceId)}`, {
          method: 'PUT',
          headers: { Authorization: `Bearer ${token}`, 'content-type': 'application/json' },
          body: JSON.stringify({ display_name: name }),
        });
        return res.ok;
      } catch {
        return false;
      }
    },
  };
}
