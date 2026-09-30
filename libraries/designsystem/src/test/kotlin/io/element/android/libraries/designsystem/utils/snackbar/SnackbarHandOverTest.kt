/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.utils.snackbar

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SnackbarHandOverTest {
    @Test
    fun `hand over re-emits the current message as not displayed with a new id`() = runTest {
        val dispatcher = SnackbarDispatcher()
        var undone = false
        val message = SnackbarMessage(0, actionResId = 1, action = { undone = true }, countdownEndsAtMillis = 10)
        dispatcher.snackbarMessage.test {
            dispatcher.post(message)
            val shown = expectMostRecentItem()!!
            shown.isDisplayed.set(true)

            dispatcher.handOver(shown)

            val handed = awaitItem()!!
            assertThat(handed.id).isNotEqualTo(message.id)
            assertThat(handed.isDisplayed.get()).isFalse()
            assertThat(handed.countdownEndsAtMillis).isEqualTo(10)
            handed.action()
            assertThat(undone).isTrue()
        }
    }

    @Test
    fun `hand over of a message that is no longer current does nothing`() = runTest {
        val dispatcher = SnackbarDispatcher()
        val first = SnackbarMessage(0)
        dispatcher.snackbarMessage.test {
            dispatcher.post(first)
            dispatcher.clear()
            assertThat(expectMostRecentItem()).isNull()
            dispatcher.handOver(first)
            expectNoEvents()
        }
    }
}
