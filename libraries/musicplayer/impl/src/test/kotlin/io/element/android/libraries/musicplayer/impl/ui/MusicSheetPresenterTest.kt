/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl.ui

import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.musicplayer.api.MusicOrder
import io.element.android.libraries.musicplayer.api.MusicPlayerState
import io.element.android.libraries.musicplayer.api.MusicRepeatMode
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import io.element.android.libraries.musicplayer.api.profileMusicQueueId
import io.element.android.libraries.musicplayer.api.toMusicTrack
import io.element.android.libraries.musicplayer.test.FakeMusicPlayer
import io.element.android.libraries.musicplayer.test.FakeProfileMusic
import io.element.android.libraries.musicplayer.test.aMusicTrack
import io.element.android.libraries.musicplayer.test.aProfileMusicTrack
import io.element.android.tests.testutils.test
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MusicSheetPresenterTest {
    private val me = UserId("@alice:server.org")
    private val tracks = listOf("a", "b", "c").map { aMusicTrack(id = it, filename = "Band - Song $it.mp3") }

    @Test
    fun `the state mirrors the queue and the current track`() = runTest {
        val player = playing(tracks, current = 1).apply {
            tags.value = persistentMapOf(tracks[1].mediaKey to MusicTrackTags(title = "Tagged", artist = "Tag Band"))
        }
        createPresenter(player).test {
            val state = awaitItem()
            assertThat(state.title).isNull()
            assertThat(state.tracks.map { it.id }).containsExactly("a", "b", "c").inOrder()
            assertThat(state.tracks.map { it.isCurrent }).containsExactly(false, true, false).inOrder()
            assertThat(state.current?.title).isEqualTo("Tagged")
            assertThat(state.current?.artist).isEqualTo("Tag Band")
            assertThat(state.tracks.first().title).isEqualTo("Song a")
            assertThat(state.profileButton).isEqualTo(ProfileButton.Add)
            assertThat(state.canRemoveTracks).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping a track plays it, tapping the current one toggles pause`() = runTest {
        val player = playing(tracks, current = 0)
        createPresenter(player).test {
            val state = awaitItem()
            state.eventSink(MusicSheetEvent.Play("c"))
            assertThat(player.playCalls.single().startTrackId).isEqualTo("c")
            assertThat(player.playCalls.single().queueId).isEqualTo("!room")
            val updated = awaitUntil { it.current?.id == "c" }
            updated.eventSink(MusicSheetEvent.Play("c"))
            assertThat(player.playPauseCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `controls go to the player`() = runTest {
        val player = playing(tracks, current = 0)
        createPresenter(player).test {
            val state = awaitItem()
            state.eventSink(MusicSheetEvent.CycleOrder)
            state.eventSink(MusicSheetEvent.CycleRepeat)
            state.eventSink(MusicSheetEvent.CycleSpeed)
            state.eventSink(MusicSheetEvent.Next)
            state.eventSink(MusicSheetEvent.Previous)
            state.eventSink(MusicSheetEvent.Seek(42_000))
            val updated = awaitUntil { it.speed == 1.5f }
            assertThat(updated.order).isEqualTo(MusicOrder.Reversed)
            assertThat(updated.repeatMode).isEqualTo(MusicRepeatMode.All)
            assertThat(updated.speed).isEqualTo(1.5f)
            assertThat(player.nextCount).isEqualTo(1)
            assertThat(player.previousCount).isEqualTo(1)
            assertThat(player.seeks).containsExactly(42_000L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `add to profile flips the button to remove`() = runTest {
        val player = playing(tracks, current = 0)
        val profileMusic = FakeProfileMusic(ownUserId = me)
        createPresenter(player, profileMusic).test {
            val state = awaitItem()
            assertThat(state.profileButton).isEqualTo(ProfileButton.Add)
            state.eventSink(MusicSheetEvent.ToggleProfile)
            val done = awaitUntil { it.profileAction is AsyncAction.Success }
            assertThat(done.profileButton).isEqualTo(ProfileButton.Remove)
            assertThat(done.profileAction).isEqualTo(AsyncAction.Success(ProfileButton.Remove))
            assertThat(profileMusic.added.single().id).isEqualTo("a")
            done.eventSink(MusicSheetEvent.ClearProfileAction)
            awaitUntil { it.profileAction == AsyncAction.Uninitialized }.eventSink(MusicSheetEvent.ToggleProfile)
            val removed = awaitUntil { it.profileAction is AsyncAction.Success }
            assertThat(removed.profileButton).isEqualTo(ProfileButton.Add)
            assertThat(profileMusic.removed.single().id).isEqualTo("a")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed profile change reports the failure`() = runTest {
        val profileMusic = FakeProfileMusic(ownUserId = me, result = Result.failure(IllegalStateException("offline")))
        createPresenter(playing(tracks, current = 0), profileMusic).test {
            awaitItem().eventSink(MusicSheetEvent.ToggleProfile)
            val failed = awaitUntil { it.profileAction is AsyncAction.Failure }
            assertThat(failed.profileAction).isInstanceOf(AsyncAction.Failure::class.java)
            assertThat(failed.profileButton).isEqualTo(ProfileButton.Add)
            failed.eventSink(MusicSheetEvent.ClearProfileAction)
            assertThat(awaitItem().profileAction).isEqualTo(AsyncAction.Uninitialized)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `own playlist allows removing tracks from the list and the queue`() = runTest {
        val profileTracks = listOf(aProfileMusicTrack(url = "mxc://s/1", title = "One"), aProfileMusicTrack(url = "mxc://s/2", title = "Two"))
        val queue = profileTracks.map { it.toMusicTrack(me) }
        val player = playing(queue, current = 0, queueId = profileMusicQueueId(me), title = "Alice — плейлист")
        val profileMusic = FakeProfileMusic(ownUserId = me, profiles = mapOf(me to profileTracks))
        createPresenter(player, profileMusic).test {
            val state = awaitUntil { it.profileButton == ProfileButton.Remove }
            assertThat(state.title).isEqualTo("Alice — плейлист")
            assertThat(state.canRemoveTracks).isTrue()
            assertThat(state.profileButton).isEqualTo(ProfileButton.Remove)
            assertThat(state.current?.title).isEqualTo("One")
            state.eventSink(MusicSheetEvent.RemoveFromProfile("mxc://s/2"))
            awaitUntil { it.profileAction is AsyncAction.Success }
            assertThat(profileMusic.removed.single().id).isEqualTo("mxc://s/2")
            assertThat(player.removeCalls).containsExactly(profileMusicQueueId(me) to "mxc://s/2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun playing(
        queue: List<MusicTrack>,
        current: Int,
        queueId: String = "!room",
        title: String? = null,
    ) = FakeMusicPlayer(
        MusicPlayerState.Idle.copy(queueId = queueId, queueTitle = title, queue = queue.toImmutableList(), currentIndex = current, isPlaying = true)
    )

    private fun createPresenter(
        player: FakeMusicPlayer,
        profileMusic: FakeProfileMusic = FakeProfileMusic(ownUserId = me),
    ) = MusicSheetPresenter(
        sessionId = me,
        musicPlayer = player,
        profileMusic = profileMusic,
        sharer = { Result.success(Unit) },
    )
}

private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
