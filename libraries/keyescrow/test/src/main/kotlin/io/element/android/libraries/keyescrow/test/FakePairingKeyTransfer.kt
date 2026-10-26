/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления при привязке устройства по QR.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.test

import io.element.android.libraries.keyescrow.api.PairingKeyTransfer
import io.element.android.libraries.keyescrow.api.SealedPairingKey

/** Без криптографии: шифротекст — «ключ|user id», K — «K». */
class FakePairingKeyTransfer(
    private val sealLambda: (String, String) -> SealedPairingKey = { recoveryKey, userId -> SealedPairingKey("K", "$recoveryKey|$userId") },
    private val openLambda: (String, String, String) -> String? = { ciphertext, key, userId ->
        ciphertext.takeIf { key == "K" && it.endsWith("|$userId") }?.substringBeforeLast('|')
    },
) : PairingKeyTransfer {
    override fun seal(recoveryKey: String, userId: String) = sealLambda(recoveryKey, userId)

    override fun open(ciphertext: String, key: String, userId: String) = openLambda(ciphertext, key, userId)
}
