// Счётчик «не чаще N раз за окно» в памяти процесса. Сервис один, пользователей десятки —
// внешнее хранилище не нужно; после перезапуска счётчики обнуляются, это допустимо.

export function createLimiter({ now = Date.now } = {}) {
  const hits = new Map(); // ключ -> метки времени обращений

  /** true, если обращение укладывается в лимит (и тогда оно засчитано). */
  function allow(key, limit, windowMs) {
    const t = now();
    const fresh = (hits.get(key) || []).filter((at) => t - at < windowMs);
    if (fresh.length >= limit) {
      hits.set(key, fresh);
      return false;
    }
    fresh.push(t);
    hits.set(key, fresh);
    return true;
  }

  /** Выбрасывает записи старше maxAgeMs, чтобы карта не росла бесконечно. */
  function sweep(maxAgeMs) {
    const t = now();
    for (const [key, list] of hits) {
      const fresh = list.filter((at) => t - at < maxAgeMs);
      if (fresh.length) hits.set(key, fresh);
      else hits.delete(key);
    }
  }

  return { allow, sweep, size: () => hits.size };
}
