/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.musicplayer.impl.MusicFocusArbiter.Action
import org.junit.Test

class MusicFocusArbiterTest {
    private val own = Any()
    private val voice = "\$voiceEvent"

    @Test
    fun `a voice message pauses playing music and music resumes after it`() {
        val arbiter = MusicFocusArbiter(own)
        assertThat(arbiter.onAudibleChanged(voice, isMusicPlaying = true)).isEqualTo(Action.Pause)
        assertThat(arbiter.onAudibleChanged(null, isMusicPlaying = false)).isEqualTo(Action.Resume)
        assertThat(arbiter.isPausedForOther).isFalse()
    }

    @Test
    fun `music paused by the user does not start after a voice message`() {
        val arbiter = MusicFocusArbiter(own)
        assertThat(arbiter.onAudibleChanged(voice, isMusicPlaying = false)).isEqualTo(Action.None)
        assertThat(arbiter.onAudibleChanged(null, isMusicPlaying = false)).isEqualTo(Action.None)
    }

    @Test
    fun `a user action cancels the automatic resume`() {
        val arbiter = MusicFocusArbiter(own)
        arbiter.onAudibleChanged(voice, isMusicPlaying = true)
        arbiter.onUserAction()
        assertThat(arbiter.onAudibleChanged(null, isMusicPlaying = false)).isEqualTo(Action.None)
    }

    @Test
    fun `the next voice message keeps music paused`() {
        val arbiter = MusicFocusArbiter(own)
        arbiter.onAudibleChanged(voice, isMusicPlaying = true)
        assertThat(arbiter.onAudibleChanged("\$nextVoice", isMusicPlaying = false)).isEqualTo(Action.None)
        assertThat(arbiter.isPausedForOther).isTrue()
    }

    @Test
    fun `music taking the focus itself changes nothing`() {
        val arbiter = MusicFocusArbiter(own)
        assertThat(arbiter.onAudibleChanged(own, isMusicPlaying = true)).isEqualTo(Action.None)
    }

    @Test
    fun `when music takes the focus back the pending resume is consumed`() {
        val arbiter = MusicFocusArbiter(own)
        arbiter.onAudibleChanged(voice, isMusicPlaying = true)
        assertThat(arbiter.onAudibleChanged(own, isMusicPlaying = true)).isEqualTo(Action.Resume)
        assertThat(arbiter.isPausedForOther).isFalse()
    }
}
