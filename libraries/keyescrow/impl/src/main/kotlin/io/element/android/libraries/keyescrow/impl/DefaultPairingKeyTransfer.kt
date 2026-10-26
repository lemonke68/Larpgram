/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления при привязке устройства по QR.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalEncodingApi::class)

package io.element.android.libraries.keyescrow.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.keyescrow.api.PairingKeyTransfer
import io.element.android.libraries.keyescrow.api.SealedPairingKey
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * AES-256-GCM на случайном K (32 байта), user id в AAD. Шифротекст: версия (1 байт) ‖ nonce
 * (12 байт) ‖ шифр с тегом, всё в base64url без паддинга. Argon2 не нужен: K случайный, а не пароль.
 */
@ContributesBinding(AppScope::class)
class DefaultPairingKeyTransfer(
    private val random: SecureRandom = SecureRandom(),
) : PairingKeyTransfer {
    override fun seal(recoveryKey: String, userId: String): SealedPairingKey {
        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(userId.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(recoveryKey.toByteArray(Charsets.UTF_8))
        val ciphertext = byteArrayOf(VERSION) + nonce + sealed
        return SealedPairingKey(key = BASE64.encode(key), ciphertext = BASE64.encode(ciphertext)).also { key.fill(0) }
    }

    override fun open(ciphertext: String, key: String, userId: String): String? = try {
        val keyBytes = BASE64.decode(key)
        val bytes = BASE64.decode(ciphertext)
        if (keyBytes.size != KEY_BYTES || bytes.size <= 1 + NONCE_BYTES || bytes[0] != VERSION) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(TAG_BITS, bytes, 1, NONCE_BYTES),
            )
            cipher.updateAAD(userId.toByteArray(Charsets.UTF_8))
            val plaintext = cipher.doFinal(bytes, 1 + NONCE_BYTES, bytes.size - 1 - NONCE_BYTES)
            keyBytes.fill(0)
            String(plaintext, Charsets.UTF_8)
        }
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        // Битый base64.
        null
    }

    private companion object {
        const val VERSION: Byte = 1
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val KEY_BYTES = 32
        const val TAG_BITS = 128
        val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    }
}
