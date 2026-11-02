/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.matrix.impl.room

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiRoomListService
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiTimeline
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomContentForwarderProtectionTest {
    @Test
    fun `protected or unavailable source policy blocks before reading or queuing content`() = runTest {
        var policy = Result.success(true)
        var sourceRead = false
        val forwarder = RoomContentForwarder(
            roomListService = FakeFfiRoomListService(),
            copyProtected = { policy.getOrThrow() },
            sourceInfo = {
                sourceRead = true
                error("Source content must not be read")
            },
        )
        suspend fun attempt() = runCatching {
            forwarder.forward(FakeFfiTimeline(), RoomId("!source:example.org"), listOf(EventId("\$event")), listOf(RoomId("!target:example.org")))
        }
        assertThat(attempt().exceptionOrNull()?.message).contains("restricted")
        assertThat(sourceRead).isFalse()
        policy = Result.failure(IllegalStateException("offline"))
        assertThat(attempt().exceptionOrNull()?.message).isEqualTo("offline")
        assertThat(sourceRead).isFalse()
    }
}
