/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления, запертый паролем (escrow вариант B).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalEncodingApi::class)

package io.element.android.libraries.keyescrow.impl

import dev.zacsweers.metro.Inject
import io.element.android.libraries.keyescrow.api.LockedRecoveryKey
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Запирает ключ восстановления паролем и отпирает обратно. Чистая криптография без сети и
 * Android: Argon2id из BouncyCastle (Java, без нативных библиотек), AES-256-GCM из JCA.
 *
 * user id идёт в AAD: блоб одного аккаунта нельзя подсунуть другому, даже с тем же паролем.
 * Argon2 с 64 МиБ — это секунда-две CPU, поэтому звать только не на главном потоке.
 */
@Inject
class RecoveryKeyLock(
    private val params: Params = Params.DEFAULT,
    private val random: SecureRandom = SecureRandom(),
) {
    data class Params(val memoryKib: Int, val iterations: Int, val parallelism: Int) {
        companion object {
            /**
             * Замер на Honor MAR-LX1M (2026-10-07): 64 МиБ × 3 — 1,5 с, 64 МиБ × 2 — 1,0 с, 32 МиБ × 3 —
             * 0,7 с. Память важнее проходов против перебора на GPU, поэтому 64 МиБ × 2. Сервер не
             * примет меньше 16 МиБ и 2 проходов.
             */
            val DEFAULT = Params(memoryKib = 64 * 1024, iterations = 2, parallelism = 1)
        }
    }

    fun lock(recoveryKey: String, password: String, userId: String): LockedRecoveryKey {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val kek = deriveKek(password, salt, params.memoryKib, params.iterations, params.parallelism)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(userId.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(recoveryKey.toByteArray(Charsets.UTF_8))
        kek.fill(0)
        return LockedRecoveryKey(
            version = VERSION,
            kdf = KDF,
            memoryKib = params.memoryKib,
            iterations = params.iterations,
            parallelism = params.parallelism,
            salt = salt.toBase64(),
            nonce = nonce.toBase64(),
            ciphertext = ciphertext.toBase64(),
        )
    }

    /**
     * Ключ восстановления или `null`, если пароль не тот (или блоб чужой, или испорчен):
     * GCM не отличает эти случаи, и вызывающему они все значат одно — этим паролем не открыть.
     */
    fun open(locked: LockedRecoveryKey, password: String, userId: String): String? {
        if (locked.version != VERSION || locked.kdf != KDF) return null
        return try {
            val kek = deriveKek(password, locked.salt.fromBase64(), locked.memoryKib, locked.iterations, locked.parallelism)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, locked.nonce.fromBase64()))
            cipher.updateAAD(userId.toByteArray(Charsets.UTF_8))
            val plaintext = cipher.doFinal(locked.ciphertext.fromBase64())
            kek.fill(0)
            String(plaintext, Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            // Битый base64 или параметры вне допустимого у Argon2.
            null
        }
    }

    private fun deriveKek(password: String, salt: ByteArray, memoryKib: Int, iterations: Int, parallelism: Int): ByteArray {
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memoryKib)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build()
        )
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val kek = ByteArray(KEY_BYTES)
        generator.generateBytes(passwordBytes, kek)
        passwordBytes.fill(0)
        return kek
    }

    private companion object {
        const val VERSION = 1
        const val KDF = "argon2id"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        const val KEY_BYTES = 32
        const val TAG_BITS = 128
    }
}

private fun ByteArray.toBase64(): String = Base64.encode(this)

private fun String.fromBase64(): ByteArray = Base64.decode(this)
