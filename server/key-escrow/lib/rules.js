// Чистые правила сервиса без сети и базы — их гоняют тесты (node --test).

// Ключ восстановления Matrix: base58 от 35 байт (2 байта префикса, 32 байта ключа, байт
// чётности) — ровно 48 символов. Клиенты показывают его группами по 4 через пробел.
const BASE58_48 = /^[1-9A-HJ-NP-Za-km-z]{48}$/;

export function isValidRecoveryKey(value) {
  if (typeof value !== 'string') return false;
  return BASE58_48.test(value.replace(/\s+/g, ''));
}

// Домен из user_id (@user:domain) или null.
export function domainOf(userId) {
  if (typeof userId !== 'string') return null;
  const colon = userId.indexOf(':');
  return colon > 0 ? userId.slice(colon + 1) : null;
}

/**
 * Можно ли «удалить у обоих» эту комнату. Purge через admin API сносит комнату только на нашем
 * сервере, а m.direct любой клиент пишет сам, поэтому проверяем всё, что клиент подделать не может:
 *  - вызывающий — участник, участников не больше двух, все с нашего сервера;
 *  - у комнаты нет имени и адреса, писать может любой участник (не канал);
 *  - комната числится личкой в m.direct у каждого участника (читаем через admin API).
 * Возвращает null, если можно, иначе причину (для лога и кода 409/403).
 */
export function deleteForBothRefusal({ callerId, members, room, directOf }) {
  if (!members.includes(callerId)) return 'not-a-member';
  if (members.length === 0 || members.length > 2) return 'not-two-people';
  const home = domainOf(callerId);
  if (!members.every((m) => domainOf(m) === home)) return 'remote-member';
  if (room.name) return 'named-room';
  if (room.canonicalAlias) return 'room-with-alias';
  if ((room.eventsDefault ?? 0) > 0) return 'channel';
  for (const member of members) {
    const direct = directOf[member];
    if (!direct) return 'not-direct';
    const listed = Object.values(direct).some((rooms) => Array.isArray(rooms) && rooms.includes(room.roomId));
    if (!listed) return 'not-direct';
  }
  return null;
}
