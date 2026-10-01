/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

import io.element.android.libraries.accountapi.api.AccountEmail

data class TgAccountPasswordState(
    val step: Step,
    /** Почта, на которую уйдёт код; `null` — ещё узнаём. */
    val email: AccountEmail?,
    val code: String,
    val resendAfterSeconds: Int,
    val codeSentCount: Int,
    val password: String,
    val passwordRepeat: String,
    val isBusy: Boolean,
    val error: TgAccountError?,
    val eventSink: (TgAccountPasswordEvent) -> Unit,
) {
    enum class Step { Intro, Code, NewPassword, Done }

    val canSubmitPassword get() = !isBusy && password.isNotEmpty() && passwordRepeat.isNotEmpty()
}

sealed interface TgAccountPasswordEvent {
    /** Прислать код на почту аккаунта. */
    data object SendCode : TgAccountPasswordEvent
    data class SetCode(val value: String) : TgAccountPasswordEvent
    data object ResendCode : TgAccountPasswordEvent
    data class SetPassword(val value: String) : TgAccountPasswordEvent
    data class SetPasswordRepeat(val value: String) : TgAccountPasswordEvent
    data object SubmitPassword : TgAccountPasswordEvent
    data object Back : TgAccountPasswordEvent
}
