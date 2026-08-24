/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.outgoing

import androidx.compose.runtime.Stable
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.verification.SessionVerificationData
import io.element.android.libraries.matrix.api.verification.VerificationRequest

data class OutgoingVerificationState(
    val step: Step,
    val request: VerificationRequest.Outgoing,
    // Правка форка: параллельный под-флоу «подтвердить по почте» (escrow). Живёт рядом со
    // step, не в SDK-машине: после успешного recover() статус сессии сам станет Verified и
    // step уедет в Completed/Exit.
    val emailStep: EmailVerifyStep,
    val eventSink: (OutgoingVerificationViewEvents) -> Unit,
) {
    @Stable
    sealed interface Step {
        data object Loading : Step
        data object Initial : Step
        data object Canceled : Step
        data object AwaitingOtherDeviceResponse : Step
        data object Ready : Step
        data class Verifying(val data: SessionVerificationData, val state: AsyncData<Unit>) : Step
        data object Completed : Step
        data object Exit : Step

        val isTimeLimited: Boolean
            get() = this is Initial ||
                this is AwaitingOtherDeviceResponse ||
                this is Ready ||
                this is Verifying
    }
}

/**
 * Правка форка: состояние под-флоу «подтвердить по почте».
 *
 * Показывается диалогами поверх обычного экрана верификации: запросили код, ввели 6 цифр,
 * сервер отдал ключ восстановления, клиент им зовёт `EncryptionService.recover`.
 */
@Stable
sealed interface EmailVerifyStep {
    /** Под-флоу не активен. */
    data object Hidden : EmailVerifyStep

    /** Просим сервер прислать код на почту. */
    data object SendingCode : EmailVerifyStep

    /** Экран ввода кода. [submitting] покрывает и проверку кода, и последующий recover. */
    data class EnterCode(
        val maskedEmail: String,
        val submitting: Boolean,
        val error: EmailVerifyError?,
    ) : EmailVerifyStep

    /** Способ недоступен: у аккаунта нет почты, в escrow нет ключа, или сервер не отвечает. */
    data class Unavailable(val reason: EmailVerifyUnavailable) : EmailVerifyStep
}

enum class EmailVerifyUnavailable {
    NoEmail,
    NoStoredKey,
    RateLimited,
    Network,
}

sealed interface EmailVerifyError {
    /** Неверный код. [attemptsLeft] — сколько попыток осталось, если сервер сказал. */
    data class InvalidCode(val attemptsLeft: Int?) : EmailVerifyError

    /** Срок кода истёк, нужен новый. */
    data object Expired : EmailVerifyError

    /** Слишком много неверных попыток, код заблокирован. */
    data object TooManyAttempts : EmailVerifyError

    /** Код верный, но сам recover не удался (например бэкап недоступен). */
    data object RecoverFailed : EmailVerifyError

    /** Сеть или сервер недоступны. */
    data object Network : EmailVerifyError
}
