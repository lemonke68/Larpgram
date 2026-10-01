/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tg

import io.element.android.features.login.impl.screens.tg.TgAuthError.Kind

/**
 * Проверки до похода на сервер — те же, что в `server/account/lib/rules.js`. Сервер проверяет ещё
 * раз, здесь они нужны, чтобы не гонять запрос ради опечатки.
 */
internal object TgAuthRules {
    const val CODE_LENGTH = 6
    private const val USERNAME_MIN = 3
    private const val USERNAME_MAX = 32
    private const val PASSWORD_MIN = 8
    private const val PASSWORD_MAX = 128
    private val username = Regex("^[a-z][a-z0-9_]*$")
    private val email = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    /** Ник, как он будет храниться: без «@» и пробелов, в нижнем регистре. */
    fun normalizeUsername(value: String) = value.trim().removePrefix("@").lowercase()

    fun usernameProblem(value: String): Kind? = when {
        value.length < USERNAME_MIN -> Kind.UsernameTooShort
        value.length > USERNAME_MAX -> Kind.UsernameTooLong
        !username.matches(value) -> Kind.UsernameInvalid
        else -> null
    }

    fun emailProblem(value: String): Kind? = if (email.matches(value)) null else Kind.EmailInvalid

    fun passwordProblem(password: String, repeat: String): Kind? = when {
        password.length < PASSWORD_MIN -> Kind.PasswordTooShort
        password.length > PASSWORD_MAX -> Kind.PasswordTooLong
        password != repeat -> Kind.PasswordsDiffer
        else -> null
    }

    /** В поле входа ввели почту, а не ник. */
    fun looksLikeEmail(value: String) = !value.startsWith("@") && value.contains('@')

    /**
     * Что уходит в Matrix-логин: почта как есть, ник — без «@» и домена (человек мог вставить
     * полный Matrix ID).
     */
    fun loginIdentifier(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("@")) trimmed.removePrefix("@").substringBefore(':') else trimmed
    }
}
