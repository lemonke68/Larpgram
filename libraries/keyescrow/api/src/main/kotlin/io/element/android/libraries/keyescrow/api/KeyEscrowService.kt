/*
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.keyescrow.api

import io.element.android.libraries.matrix.api.core.RoomId

/**
 * Серверное депонирование ключа восстановления, чтобы верифицировать новую сессию кодом
 * с почты, а не вторым устройством.
 *
 * Идея: ключ восстановления кладётся на сервер (эндпоинт escrow), и на новом устройстве
 * человек вводит код, пришедший на почту, сервер отдаёт ключ, а клиент им зовёт
 * `EncryptionService.recover(...)` — это разом верифицирует сессию и открывает бэкап.
 *
 * ВНИМАНИЕ, это осознанный компромисс модели безопасности: сервер и тот, кто получит
 * доступ к почтовому ящику, технически смогут получить ключ и читать переписку. Для
 * телеграм-подобного клиента с доверием своему серверу это принято сознательно (см. README,
 * раздел про вход по коду с почты). Авторизация — тем же access-токеном Matrix, что уже есть
 * у свежей (ещё не верифицированной) сессии; адрес почты сервер берёт сам из /account/3pid,
 * чтобы код нельзя было увести на чужой ящик.
 */
interface KeyEscrowService {
    /**
     * Лежит ли уже наш ключ в хранилище. `null` — сервер недоступен или ответил невнятно
     * (не путать с «ключа нет»: в этом случае бэкфилл не запускаем, чтобы не сбрасывать
     * ключ зря).
     */
    suspend fun hasStoredKey(): Boolean?

    /** Залить ключ восстановления в хранилище, перезаписав прежний. */
    suspend fun store(recoveryKey: String): Result<Unit>

    /** Попросить сервер прислать 6-значный код на почту аккаунта. */
    suspend fun requestCode(): RequestCodeResult

    /** Проверить код и получить ключ восстановления. */
    suspend fun redeemCode(code: String): RedeemResult

    /**
     * Правка форка: «удалить у обоих» для ЛС. Просит наш сервер снести комнату целиком через
     * Synapse admin API — Matrix не даёт удалить чужую сторону. Сервер сам проверяет, что это
     * личка (2 участника) и что мы в ней состоим. `true` — сервер принял задачу (202).
     */
    suspend fun deleteDmForBoth(roomId: RoomId): Boolean
}

/** Результат запроса кода на почту. */
sealed interface RequestCodeResult {
    /** Код отправлен. [maskedEmail] — адрес в виде `a***@b.ru` для подсказки на экране. */
    data class Sent(val maskedEmail: String) : RequestCodeResult

    /** У аккаунта нет привязанной почты — этим способом верифицироваться нельзя. */
    data object NoEmail : RequestCodeResult

    /** Слишком часто просят код, сервер попросил подождать. */
    data object RateLimited : RequestCodeResult

    /** Сеть или сервер недоступны. */
    data object NetworkError : RequestCodeResult
}

/** Результат проверки кода. */
sealed interface RedeemResult {
    /** Код верный, [recoveryKey] можно скармливать в `EncryptionService.recover`. */
    data class Success(val recoveryKey: String) : RedeemResult

    /** Код неверный. [attemptsLeft] — сколько попыток осталось до блокировки (если сервер сказал). */
    data class InvalidCode(val attemptsLeft: Int?) : RedeemResult

    /** Срок кода истёк, надо запросить новый. */
    data object Expired : RedeemResult

    /** Слишком много неверных попыток, код заблокирован — запросить новый. */
    data object TooManyAttempts : RedeemResult

    /** В хранилище нет ключа для этого аккаунта (нечего отдавать). */
    data object NoStoredKey : RedeemResult

    /** Сеть или сервер недоступны. */
    data object NetworkError : RedeemResult
}
