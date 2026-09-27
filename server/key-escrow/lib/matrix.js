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

// { userId, deviceId } или null, если токен не годится (401/403). Недоступность Synapse, 429 и
// 5xx — исключение: мидлварь отвечает 502, а не «плохой токен».
export async function whoami(token) {
  const res = await fetch(`${HS}/_matrix/client/v3/account/whoami`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status === 401 || res.status === 403) return null;
  if (!res.ok) throw new Error(`whoami HTTP ${res.status}`);
  const body = await res.json();
  return body?.user_id ? { userId: body.user_id, deviceId: body.device_id || null } : null;
}

// Первый привязанный email или null.
export async function getEmail(token) {
  const body = await get('/_matrix/client/v3/account/3pid', token).catch(() => null);
  const threepids = body?.threepids || [];
  const email = threepids.find((p) => p.medium === 'email');
  return email?.address || null;
}

// m.direct пользователя через admin API (объект «собеседник → комнаты»); {} если нет; null при ошибке.
// Читаем у каждого участника сами, а не верим клиенту: m.direct клиент пишет как хочет.
export async function getDirectRoomsOf(userId) {
  const res = await adminFetch(`/_synapse/admin/v1/users/${encodeURIComponent(userId)}/accountdata`).catch(() => null);
  if (!res || !res.ok) return null;
  const body = await res.json().catch(() => null);
  const direct = body?.account_data?.global?.['m.direct'];
  return direct && typeof direct === 'object' ? direct : {};
}

// Имя, адрес и events_default комнаты через admin API; null при ошибке.
export async function getRoomDetails(roomId) {
  const id = encodeURIComponent(roomId);
  const [infoRes, stateRes] = await Promise.all([
    adminFetch(`/_synapse/admin/v1/rooms/${id}`).catch(() => null),
    adminFetch(`/_synapse/admin/v1/rooms/${id}/state`).catch(() => null),
  ]);
  if (!infoRes?.ok || !stateRes?.ok) return null;
  const info = await infoRes.json().catch(() => null);
  const state = await stateRes.json().catch(() => null);
  if (!info || !Array.isArray(state?.state)) return null;
  const powerLevels = state.state.find((e) => e.type === 'm.room.power_levels' && e.state_key === '');
  return {
    roomId,
    name: info.name || null,
    canonicalAlias: info.canonical_alias || null,
    eventsDefault: Number(powerLevels?.content?.events_default ?? 0),
  };
}
