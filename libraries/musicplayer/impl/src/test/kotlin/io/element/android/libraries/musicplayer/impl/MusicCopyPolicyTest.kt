/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.musicplayer.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.musicplayer.test.aMusicTrack
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MusicCopyPolicyTest {
    @Test
    fun `export checks the originating account and rereads its policy`() = runTest {
        val sourceSession = SessionId("@source:example.org")
        val track = aMusicTrack().copy(sessionId = sourceSession)
        var blocked = true
        val sourceClient = FakeMatrixClient().apply { getRoomCopyProtectionLambda = { Result.success(blocked) } }
        val selectedSessions = mutableListOf<SessionId>()
        val policy = MusicCopyPolicy(FakeMatrixClientProvider { session ->
            selectedSessions.add(session)
            check(session == sourceSession)
            Result.success(sourceClient)
        })
        assertThat(runCatching { policy.requireAllowed(track) }.isFailure).isTrue()
        blocked = false
        policy.requireAllowed(track)
        sourceClient.getRoomCopyProtectionLambda = { Result.failure(IllegalStateException("offline")) }
        assertThat(runCatching { policy.requireAllowed(track) }.isFailure).isTrue()
        assertThat(selectedSessions).containsExactly(sourceSession, sourceSession, sourceSession)
    }
}
