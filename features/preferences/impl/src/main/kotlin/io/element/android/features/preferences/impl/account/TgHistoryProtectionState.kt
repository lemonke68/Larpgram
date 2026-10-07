/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

/**
 * Экран «Защита истории» (escrow вариант B): заперт ли ключ восстановления паролем, показать ключ,
 * тумблер «восстановление через сервер».
 */
data class TgHistoryProtectionState(
    /** `null` — ещё узнаём или сервер не ответил (см. [isLoading]). */
    val status: Status?,
    val isLoading: Boolean,
    val password: String,
    val isBusy: Boolean,
    /** Ключ восстановления, если его попросили показать. */
    val recoveryKey: String?,
    val error: Error?,
    val eventSink: (TgHistoryProtectionEvent) -> Unit,
) {
    data class Status(
        /** Ключ на сервере заперт паролем, этому устройству пароль больше не нужен. */
        val lockedWithPassword: Boolean,
        val serverRecovery: Boolean,
    )

    enum class Error {
        WrongPassword,
        Network,

        /** На этом устройстве нет ключа: показать или отдать серверу нечего. */
        NoKeyOnDevice,
    }

    val canSubmit get() = !isBusy && password.isNotEmpty()
}

sealed interface TgHistoryProtectionEvent {
    data class SetPassword(val value: String) : TgHistoryProtectionEvent
    data object SubmitPassword : TgHistoryProtectionEvent
    data object ShowKey : TgHistoryProtectionEvent
    data object HideKey : TgHistoryProtectionEvent
    data class SetServerRecovery(val enabled: Boolean) : TgHistoryProtectionEvent
    data object Retry : TgHistoryProtectionEvent
}
