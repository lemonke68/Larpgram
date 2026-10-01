/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tg

import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.impl.screens.tg.TgAuthError.Kind
import org.junit.Test

/** Те же случаи, что в `server/account/test/rules.test.js`: клиент и сервер должны судить одинаково. */
class TgAuthRulesTest {
    @Test
    fun `username is stored lower case without the at sign`() {
        assertThat(TgAuthRules.normalizeUsername("  @Vasya_01 ")).isEqualTo("vasya_01")
    }

    @Test
    fun `username rules`() {
        assertThat(TgAuthRules.usernameProblem("vasya")).isNull()
        assertThat(TgAuthRules.usernameProblem("v_1")).isNull()
        assertThat(TgAuthRules.usernameProblem("ab")).isEqualTo(Kind.UsernameTooShort)
        assertThat(TgAuthRules.usernameProblem("a".repeat(33))).isEqualTo(Kind.UsernameTooLong)
        assertThat(TgAuthRules.usernameProblem("1vasya")).isEqualTo(Kind.UsernameInvalid)
        assertThat(TgAuthRules.usernameProblem("vasya.p")).isEqualTo(Kind.UsernameInvalid)
        assertThat(TgAuthRules.usernameProblem("вася")).isEqualTo(Kind.UsernameInvalid)
    }

    @Test
    fun `email rules`() {
        assertThat(TgAuthRules.emailProblem("vasya@example.com")).isNull()
        assertThat(TgAuthRules.emailProblem("vasya@example")).isEqualTo(Kind.EmailInvalid)
        assertThat(TgAuthRules.emailProblem("va sya@example.com")).isEqualTo(Kind.EmailInvalid)
        assertThat(TgAuthRules.emailProblem("a@b@example.com")).isEqualTo(Kind.EmailInvalid)
    }

    @Test
    fun `password rules`() {
        assertThat(TgAuthRules.passwordProblem("12345678", "12345678")).isNull()
        assertThat(TgAuthRules.passwordProblem("1234567", "1234567")).isEqualTo(Kind.PasswordTooShort)
        assertThat(TgAuthRules.passwordProblem("x".repeat(129), "x".repeat(129))).isEqualTo(Kind.PasswordTooLong)
        assertThat(TgAuthRules.passwordProblem("12345678", "12345679")).isEqualTo(Kind.PasswordsDiffer)
    }

    @Test
    fun `login identifier keeps email, strips at sign and server from a nick`() {
        assertThat(TgAuthRules.loginIdentifier(" vasya ")).isEqualTo("vasya")
        assertThat(TgAuthRules.loginIdentifier("@vasya")).isEqualTo("vasya")
        assertThat(TgAuthRules.loginIdentifier("@vasya:mango-kokos.ru")).isEqualTo("vasya")
        assertThat(TgAuthRules.loginIdentifier("Vasya@Example.com")).isEqualTo("Vasya@Example.com")
    }
}
