/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.outgoing

sealed interface OutgoingVerificationViewEvents {
    data object RequestVerification : OutgoingVerificationViewEvents
    data object StartSasVerification : OutgoingVerificationViewEvents
    data object ConfirmVerification : OutgoingVerificationViewEvents
    data object DeclineVerification : OutgoingVerificationViewEvents
    data object Cancel : OutgoingVerificationViewEvents
    data object Reset : OutgoingVerificationViewEvents

    // Правка форка: под-флоу «подтвердить по почте» (escrow).
    /** Нажали «Подтвердить по почте»: просим сервер прислать код. */
    data object StartEmailVerification : OutgoingVerificationViewEvents

    /** Ввели код и подтвердили. */
    data class SubmitEmailCode(val code: String) : OutgoingVerificationViewEvents

    /** Закрыли под-флоу почты. */
    data object DismissEmailVerification : OutgoingVerificationViewEvents
}
