/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.initializer

import android.util.Log
import io.element.android.libraries.matrix.api.tracing.LogLevel
import timber.log.Timber

/**
 * Drops logs below the tracing level before they reach [delegate].
 *
 * The SDK tree sends every log over FFI (JNA, with reflection) and only the Rust side filters
 * by level, so in a release build every `Timber.d` still cost a native call on the calling
 * thread: about a tenth of the main thread while a chat opens on the Honor. Filtering here
 * also skips the message formatting.
 */
internal class LevelFilteredTree(
    private val delegate: Timber.Tree,
    logLevel: LogLevel,
) : Timber.Tree() {
    private val minPriority = when (logLevel) {
        LogLevel.TRACE -> Log.VERBOSE
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARN -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
    }

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= minPriority

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // [message] already carries the formatted arguments and the stack trace of [t].
        delegate.log(priority, null as Throwable?, if (tag != null) "[$tag] $message" else message)
    }
}
