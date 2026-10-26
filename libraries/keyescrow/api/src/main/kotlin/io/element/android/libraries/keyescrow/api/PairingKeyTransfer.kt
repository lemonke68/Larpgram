/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления при привязке устройства по QR.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.api

/**
 * Передача ключа восстановления новому устройству при входе по QR. Вошедшее устройство шифрует
 * ключ случайным ключом K: шифротекст уходит на сервер вместе с кодом привязки, K — только в QR.
 * Сервер без K ключ не откроет, а QR без сервера бесполезен (код одноразовый, живёт 2 минуты).
 */
interface PairingKeyTransfer {
    /** Шифрует [recoveryKey] новым K; user id идёт в AAD. */
    fun seal(recoveryKey: String, userId: String): SealedPairingKey

    /** Ключ восстановления или `null`, если K, user id или шифротекст не те. */
    fun open(ciphertext: String, key: String, userId: String): String?
}

/** [key] — K для QR, [ciphertext] — для сервера. Оба в base64url без паддинга. */
data class SealedPairingKey(
    val key: String,
    val ciphertext: String,
)
