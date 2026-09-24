/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.interceptors

import io.element.android.libraries.core.extensions.ellipsize
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

internal class FormattedJsonHttpLogger(
    private val level: HttpLoggingInterceptor.Level
) : HttpLoggingInterceptor.Logger {
    companion object {
        private const val INDENT_SPACE = 2

        // Правка форка: тела запросов в debug-логе не должны нести секреты — ключ восстановления
        // (escrow), пароли, токены, код с почты. Логи уходят в rageshake-файлы.
        private val SECRET_JSON_FIELD = Regex("""("(?:recovery_key|password|access_token|refresh_token|code)"\s*:\s*)"[^"]*"""")

        private val AUTHORIZATION_HEADER = Regex("""^(Authorization:\s*).+""", RegexOption.IGNORE_CASE)

        internal fun redactSecrets(message: String): String = SECRET_JSON_FIELD
            .replace(message) { "${it.groupValues[1]}\"***\"" }
            .replace(AUTHORIZATION_HEADER) { "${it.groupValues[1]}***" }
    }

    /**
     * Log the message and try to log it again as a JSON formatted string.
     * Note: it can consume a lot of memory but it is only in DEBUG mode.
     *
     * @param message
     */
    @Synchronized
    override fun log(message: String) {
        val redacted = redactSecrets(message)
        Timber.d(redacted.ellipsize(200_000))

        // Try to log formatted Json only if there is a chance that [message] contains Json.
        // It can be only the case if we log the bodies of Http requests.
        if (level != HttpLoggingInterceptor.Level.BODY) return

        if (redacted.length > 100_000) {
            Timber.d("Content is too long (${redacted.length} chars) to be formatted as JSON")
            return
        }

        if (redacted.startsWith("{")) {
            // JSON Detected
            try {
                val o = JSONObject(redacted)
                logJson(o.toString(INDENT_SPACE))
            } catch (e: JSONException) {
                // Finally this is not a JSON string...
                Timber.e(e)
            }
        } else if (redacted.startsWith("[")) {
            // JSON Array detected
            try {
                val o = JSONArray(redacted)
                logJson(o.toString(INDENT_SPACE))
            } catch (e: JSONException) {
                // Finally not JSON...
                Timber.e(e)
            }
        }
        // Else not a json string to log
    }

    private fun logJson(formattedJson: String) {
        formattedJson
            .lines()
            .dropLastWhile { it.isEmpty() }
            .forEach { Timber.v(it) }
    }
}
