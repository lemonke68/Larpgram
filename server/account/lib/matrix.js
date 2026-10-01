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
