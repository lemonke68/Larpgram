/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления при привязке устройства по QR.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultPairingKeyTransferTest {
    private val transfer = DefaultPairingKeyTransfer()

    @Test
    fun `the key from the QR opens the ciphertext`() {
        val sealed = transfer.seal(KEY, USER)
        assertThat(transfer.open(sealed.ciphertext, sealed.key, USER)).isEqualTo(KEY)
        // 32 байта K в base64url без паддинга, оба поля — без символов вне base64url.
        assertThat(sealed.key).hasLength(43)
        assertThat(sealed.key).matches("[A-Za-z0-9_-]+")
        assertThat(sealed.ciphertext).matches("[A-Za-z0-9_-]+")
        assertThat(sealed.ciphertext).doesNotContain(KEY.substring(0, 4))
    }

    @Test
    fun `every seal uses a fresh key and nonce`() {
        val first = transfer.seal(KEY, USER)
        val second = transfer.seal(KEY, USER)
        assertThat(first.key).isNotEqualTo(second.key)
        assertThat(first.ciphertext).isNotEqualTo(second.ciphertext)
    }

    @Test
    fun `a wrong key does not open it`() {
        val sealed = transfer.seal(KEY, USER)
        val other = transfer.seal(KEY, USER)
        assertThat(transfer.open(sealed.ciphertext, other.key, USER)).isNull()
    }

    @Test
    fun `another account does not open it`() {
        val sealed = transfer.seal(KEY, USER)
        assertThat(transfer.open(sealed.ciphertext, sealed.key, "@mallory:mango-kokos.ru")).isNull()
    }

    @Test
    fun `tampered or broken input does not open`() {
        val sealed = transfer.seal(KEY, USER)
        val middle = sealed.ciphertext.length / 2
        val flipped = sealed.ciphertext.replaceRange(middle, middle + 1, if (sealed.ciphertext[middle] == 'A') "B" else "A")
        assertThat(transfer.open(flipped, sealed.key, USER)).isNull()
        assertThat(transfer.open("not base64!", sealed.key, USER)).isNull()
        assertThat(transfer.open(sealed.ciphertext, "c2hvcnQ", USER)).isNull()
        assertThat(transfer.open("AQ", sealed.key, USER)).isNull()
        // Другая версия формата.
        val v2 = "Ag" + sealed.ciphertext.drop(2)
        assertThat(transfer.open(v2, sealed.key, USER)).isNull()
    }

    private companion object {
        const val USER = "@petya:mango-kokos.ru"
        const val KEY = "EsTc 5rr1 4fJY BvG1 x8Ci ZcYa 3PdS Xa6A PdKx zEsK q7Ap yupT"
    }
}
