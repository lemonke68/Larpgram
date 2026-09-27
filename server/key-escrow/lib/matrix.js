// Обращения к Matrix-серверу (Synapse) для авторизации и адреса почты.
//
// Токен клиента мы не проверяем сами, а спрашиваем сервер: whoami возвращает user_id, если
// токен живой. Почту берём из /account/3pid — так код уходит только на реальный адрес
// аккаунта, а не на тот, что назвал клиент. MAS прокидывает подтверждённый адрес в Synapse,
// поэтому CS API его видит (проверено в клиентском модуле accountemail).

const HS = (process.env.HOMESERVER_URL || 'https://matrix.mango-kokos.ru').replace(/\/+$/, '');

// Токен серверного админа (не клиента): им сервис ходит в Synapse admin API для «удалить у обоих».
const ADMIN_TOKEN = (process.env.SYNAPSE_ADMIN_TOKEN || '').trim();

async function get(path, token) {
  const res = await fetch(`${HS}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) return null;
  return res.json();
}

async function adminFetch(path, options = {}) {
  return fetch(`${HS}${path}`, {
    ...options,
    headers: { Authorization: `Bearer ${ADMIN_TOKEN}`, ...(options.headers || {}) },
  });
}

export function hasAdminToken() {
  return ADMIN_TOKEN.length > 0;
}

// Список user_id участников комнаты через admin API; null при ошибке.
export async function getRoomMembers(roomId) {
  const res = await adminFetch(`/_synapse/admin/v1/rooms/${encodeURIComponent(roomId)}/members`);
  if (!res.ok) return null;
  const body = await res.json().catch(() => null);
  return Array.isArray(body?.members) ? body.members : null;
}

// Удалить комнату целиком у всех (admin API v2, async, с очисткой истории). true при успехе.
export async function deleteRoom(roomId) {
  const res = await adminFetch(`/_synapse/admin/v2/rooms/${encodeURIComponent(roomId)}`, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ purge: true, block: false }),
  });
  return res.ok;
}

// user_id или null, если токен не годится.
export async function whoami(token) {
  const body = await get('/_matrix/client/v3/account/whoami', token).catch(() => null);
  return body?.user_id || null;
}

// Первый привязанный email или null.
export async function getEmail(token) {
  const body = await get('/_matrix/client/v3/account/3pid', token).catch(() => null);
  const threepids = body?.threepids || [];
  const email = threepids.find((p) => p.medium === 'email');
  return email?.address || null;
}

// Комната помечена личкой в m.direct вызывающего? Так «удалить у обоих» не заденет группу,
// где случайно остались двое. null — не смогли прочитать account data.
export async function isDirectRoom(token, userId, roomId) {
  const res = await fetch(
    `${HS}/_matrix/client/v3/user/${encodeURIComponent(userId)}/account_data/m.direct`,
    { headers: { Authorization: `Bearer ${token}` } },
  ).catch(() => null);
  if (!res) return null;
  if (res.status === 404) return false; // ни одной лички
  if (!res.ok) return null;
  const body = await res.json().catch(() => null);
  if (!body || typeof body !== 'object') return null;
  return Object.values(body).some((rooms) => Array.isArray(rooms) && rooms.includes(roomId));
}
