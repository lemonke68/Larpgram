/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: секреты не должны попадать в debug-лог HTTP.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.interceptors

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormattedJsonHttpLoggerTest {
    @Test
    fun `recovery key, password and code are masked in json bodies`() {
        val body = """{"recovery_key":"EsTb QhYk","password": "hunter2","code":"123456","errcode":"M_FORBIDDEN"}"""
        assertThat(FormattedJsonHttpLogger.redactSecrets(body))
            .isEqualTo("""{"recovery_key":"***","password":"***","code":"***","errcode":"M_FORBIDDEN"}""")
    }

    @Test
    fun `authorization header is masked`() {
        assertThat(FormattedJsonHttpLogger.redactSecrets("Authorization: Bearer mct_secret"))
            .isEqualTo("Authorization: ***")
    }

    @Test
    fun `escaped strings and nested secrets are fully masked`() {
        val body = """ {"password":"start\"secret-tail","nested":[{"access_token":"slash\\tail","refresh_token":"line\nsecret"}],"ok":true}"""
        assertThat(FormattedJsonHttpLogger.redactSecrets(body))
            .isEqualTo("""{"password":"***","nested":[{"access_token":"***","refresh_token":"***"}],"ok":true}""")
    }

    @Test
    fun `escaped field names and non string secret values are masked`() {
        val body = """[{"pass\u0077ord":{"value":"secret"},"code":123456,"recovery_key":null}]"""
        assertThat(FormattedJsonHttpLogger.redactSecrets(body))
            .isEqualTo("""[{"password":"***","code":"***","recovery_key":"***"}]""")
    }

    @Test
    fun `malformed json is omitted rather than leaking secrets`() {
        assertThat(FormattedJsonHttpLogger.redactSecrets("""{"password":"secret"""))
            .isEqualTo("[Invalid JSON body omitted]")
    }

    @Test
    fun `ordinary http log lines are preserved`() {
        assertThat(FormattedJsonHttpLogger.redactSecrets("Content-Type: application/json"))
            .isEqualTo("Content-Type: application/json")
    }
}
