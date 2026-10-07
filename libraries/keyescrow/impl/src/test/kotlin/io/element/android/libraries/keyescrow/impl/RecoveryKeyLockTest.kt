/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления, запертый паролем (escrow вариант B).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecoveryKeyLockTest {
    private val lock = RecoveryKeyLock(RecoveryKeyLock.Params(memoryKib = 16 * 1024, iterations = 2, parallelism = 1))

    @Test
    fun `the right password opens the key`() {
        val locked = lock.lock(KEY, "correct horse", USER)
        assertThat(lock.open(locked, "correct horse", USER)).isEqualTo(KEY)
        assertThat(locked.memoryKib).isEqualTo(16 * 1024)
        assertThat(locked.kdf).isEqualTo("argon2id")
    }

    @Test
    fun `a wrong password or another account does not open it`() {
        val locked = lock.lock(KEY, "correct horse", USER)
        assertThat(lock.open(locked, "correct horsE", USER)).isNull()
        assertThat(lock.open(locked, "correct horse", "@mallory:mango-kokos.ru")).isNull()
    }

    @Test
    fun `a tampered or broken blob does not open`() {
        val locked = lock.lock(KEY, "pw", USER)
        val flipped = locked.ciphertext.replaceFirstChar { if (it == 'A') 'B' else 'A' }
        assertThat(lock.open(locked.copy(ciphertext = flipped), "pw", USER)).isNull()
        assertThat(lock.open(locked.copy(salt = "not base64!"), "pw", USER)).isNull()
        assertThat(lock.open(locked.copy(version = 2), "pw", USER)).isNull()
        assertThat(lock.open(locked.copy(iterations = 3), "pw", USER)).isNull()
    }

    @Test
    fun `every lock uses a fresh salt and nonce`() {
        val first = lock.lock(KEY, "pw", USER)
        val second = lock.lock(KEY, "pw", USER)
        assertThat(first.salt).isNotEqualTo(second.salt)
        assertThat(first.nonce).isNotEqualTo(second.nonce)
    }

    @Test
    fun `default parameters pass the server floor`() {
        val params = RecoveryKeyLock.Params.DEFAULT
        assertThat(params.memoryKib).isAtLeast(16 * 1024)
        assertThat(params.iterations).isAtLeast(2)
    }

    private companion object {
        const val KEY = "EsTc 5rr1 4fJY BvG1 x8Ci ZcYa 3PdS Xa6A PdKx zEsK q7Ap yupT"
        const val USER = "@alice:mango-kokos.ru"
    }
}
