/*
 * Правка форка: секреты не должны попадать в debug-лог HTTP.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.network.interceptors

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormattedJsonHttpLoggerTest {
    @Test
    fun `recovery key, password and code are masked in json bodies`() {
        val body = """{"recovery_key":"EsTb QhYk","password": "hunter2","code":"123456","errcode":"M_FORBIDDEN"}"""
        assertThat(FormattedJsonHttpLogger.redactSecrets(body))
            .isEqualTo("""{"recovery_key":"***","password": "***","code":"***","errcode":"M_FORBIDDEN"}""")
    }

    @Test
    fun `authorization header is masked`() {
        assertThat(FormattedJsonHttpLogger.redactSecrets("Authorization: Bearer mct_secret"))
            .isEqualTo("Authorization: ***")
    }
}
