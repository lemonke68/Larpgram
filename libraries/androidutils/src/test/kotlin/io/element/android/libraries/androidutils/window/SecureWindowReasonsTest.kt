/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.androidutils.window

import android.app.Activity
import android.view.Window
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureWindowReasonsTest {
    @Test
    fun `leaving a channel preserves the app lock and another visible protected screen`() {
        val window = Robolectric.buildActivity(Activity::class.java).setup().get().window
        val channel = Any()
        val viewer = Any()
        SecureWindowReasons.set(window, SecureWindowReasons.appLock, true)
        SecureWindowReasons.set(window, channel, true)
        SecureWindowReasons.set(window, viewer, true)
        SecureWindowReasons.set(window, channel, false)
        assertThat(window.secure()).isTrue()
        SecureWindowReasons.set(window, SecureWindowReasons.appLock, false)
        assertThat(window.secure()).isTrue()
        SecureWindowReasons.set(window, viewer, false)
        assertThat(window.secure()).isFalse()
    }

    @Test
    fun `a preexisting secure window remains secure after the last lease`() {
        val window = Robolectric.buildActivity(Activity::class.java).setup().get().window
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val channel = Any()
        SecureWindowReasons.set(window, channel, true)
        SecureWindowReasons.set(window, channel, false)
        assertThat(window.secure()).isTrue()
    }
}

private fun Window.secure() = attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
