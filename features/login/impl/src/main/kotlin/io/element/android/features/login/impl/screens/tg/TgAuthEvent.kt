/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tg

sealed interface TgAuthEvent {
    /** «Начать» на приветствии. */
    data object Start : TgAuthEvent
    data object OpenRegister : TgAuthEvent
    data object OpenForgot : TgAuthEvent
    data object OpenQrScan : TgAuthEvent

    /** Шаг назад внутри экрана. С первого шага уводит узел, а не презентер. */
    data object Back : TgAuthEvent

    data class SetLogin(val value: String) : TgAuthEvent
    data class SetPassword(val value: String) : TgAuthEvent
    data object SubmitLogin : TgAuthEvent

    data class SetRegisterUsername(val value: String) : TgAuthEvent
    data class SetRegisterEmail(val value: String) : TgAuthEvent
    data class SetRegisterPassword(val value: String) : TgAuthEvent
    data class SetRegisterPasswordRepeat(val value: String) : TgAuthEvent
    data object SubmitRegister : TgAuthEvent

    data class SetForgotLogin(val value: String) : TgAuthEvent
    data object SubmitForgot : TgAuthEvent

    /** Шесть цифр отправляются сами, кнопки «Далее» на шаге кода нет. */
    data class SetCode(val value: String) : TgAuthEvent
    data object ResendCode : TgAuthEvent

    data class SetNewPassword(val value: String) : TgAuthEvent
    data class SetNewPasswordRepeat(val value: String) : TgAuthEvent
    data object SubmitNewPassword : TgAuthEvent

    /** Камера прочитала QR-код. [deviceName] — как это устройство назвать в списке сеансов. */
    data class QrScanned(val text: String, val deviceName: String) : TgAuthEvent

    data object ClearError : TgAuthEvent
    data object VersionClick : TgAuthEvent
}
