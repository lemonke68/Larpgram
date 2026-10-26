/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: регистрация и сброс пароля из приложения (сервис server/account).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountapi.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.accountapi.api.LoginQrCode
import io.element.android.libraries.accountapi.api.LoginQrPayload
import org.junit.Test

class LoginQrCodeTest {
    @Test
    fun `old format without a key still works`() {
        assertThat(LoginQrCode.encode("CODE")).isEqualTo("larpgram-login:CODE")
        assertThat(LoginQrCode.decode("larpgram-login:CODE")).isEqualTo(LoginQrPayload("CODE", null))
        assertThat(LoginQrCode.decode("  larpgram-login:CODE\n")).isEqualTo(LoginQrPayload("CODE", null))
    }

    @Test
    fun `the key rides after the hash`() {
        val text = LoginQrCode.encode("CODE", "aB3_-x")
        assertThat(text).isEqualTo("larpgram-login:CODE#aB3_-x")
        assertThat(LoginQrCode.decode(text)).isEqualTo(LoginQrPayload("CODE", "aB3_-x"))
        assertThat(LoginQrCode.encode("CODE", "")).isEqualTo("larpgram-login:CODE")
    }

    @Test
    fun `a broken key is dropped, the code is kept`() {
        assertThat(LoginQrCode.decode("larpgram-login:CODE#")).isEqualTo(LoginQrPayload("CODE", null))
        assertThat(LoginQrCode.decode("larpgram-login:CODE#not base64!")).isEqualTo(LoginQrPayload("CODE", null))
    }

    @Test
    fun `foreign or empty codes are not ours`() {
        assertThat(LoginQrCode.decode("https://example.org")).isNull()
        assertThat(LoginQrCode.decode("larpgram-login:")).isNull()
        assertThat(LoginQrCode.decode("larpgram-login:#key")).isNull()
        assertThat(LoginQrCode.decode("larpgram-login: ")).isNull()
    }
}
