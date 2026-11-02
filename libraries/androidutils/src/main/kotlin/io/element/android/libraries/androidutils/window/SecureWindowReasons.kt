/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.androidutils.window

import android.view.Window
import android.view.WindowManager
import java.util.WeakHashMap

/** Main-thread window flag leases. A channel must not clear the PIN's secure flag. */
object SecureWindowReasons {
    private data class Entry(val originallySecure: Boolean, val reasons: MutableSet<Any> = mutableSetOf())
    private val windows = WeakHashMap<Window, Entry>()
    val appLock: Any = Any()

    fun set(window: Window, reason: Any, enabled: Boolean) {
        val entry = windows[window] ?: if (enabled) {
            Entry(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0).also { windows[window] = it }
        } else {
            return
        }
        if (enabled) entry.reasons.add(reason) else entry.reasons.remove(reason)
        if (entry.originallySecure || entry.reasons.isNotEmpty()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (entry.reasons.isEmpty()) windows.remove(window)
    }
}
