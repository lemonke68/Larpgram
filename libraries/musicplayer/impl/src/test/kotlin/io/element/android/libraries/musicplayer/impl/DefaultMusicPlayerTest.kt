/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.media.AudiblePlaybackController
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.musicplayer.api.MusicRepeatMode
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import io.element.android.libraries.musicplayer.test.aMusicTrack
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Настоящий ExoPlayer под Robolectric без prepare-загрузки: навигация по плейлисту, shuffle и
 * repeat у него синхронные, файлы не нужны.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMusicPlayerTest : RobolectricTest() {
    private val tracks = listOf("a", "b", "c", "d").map { aMusicTrack(id = it, filename = "Artist - Song $it.mp3") }
    private lateinit var exoPlayer: Player

    @After
    fun tearDown() {
        AudiblePlaybackController.current.value?.let { AudiblePlaybackController.release(it) }
        if (::exoPlayer.isInitialized) exoPlayer.release()
    }

    @Test
    fun `play builds the room queue from the tapped track`() = runTest {
        val connector = FakeMusicServiceConnector()
        val player = createPlayer(connector = connector)
        player.play("!room", tracks, startTrackId = "b")
        val state = player.state.value
        assertThat(state.queueId).isEqualTo("!room")
        assertThat(state.queue.map { it.id }).containsExactly("a", "b", "c", "d").inOrder()
        assertThat(state.currentTrack?.id).isEqualTo("b")
        assertThat(state.hasNext).isTrue()
        assertThat(state.hasPrevious).isTrue()
        assertThat(exoPlayer.playWhenReady).isTrue()
        assertThat(connector.isConnected).isTrue()
        assertThat(AudiblePlaybackController.current.value).isEqualTo(MusicAudibleToken)
    }

    @Test
    fun `next and previous move through the queue`() = runTest {
        val player = createPlayer()
        player.play("!room", tracks, startTrackId = "c")
        player.skipToNext()
        assertThat(player.state.value.currentTrack?.id).isEqualTo("d")
        assertThat(player.state.value.hasNext).isFalse()
        // Конец очереди без повтора: дальше некуда.
        player.skipToNext()
        assertThat(player.state.value.currentTrack?.id).isEqualTo("d")
        player.skipToPrevious()
        assertThat(player.state.value.currentTrack?.id).isEqualTo("c")
    }

    @Test
    fun `repeat all wraps the queue and repeat one keeps the track`() = runTest {
        val player = createPlayer()
        player.play("!room", tracks, startTrackId = "d")
        player.cycleRepeatMode()
        assertThat(player.state.value.repeatMode).isEqualTo(MusicRepeatMode.All)
        assertThat(exoPlayer.repeatMode).isEqualTo(Player.REPEAT_MODE_ALL)
        assertThat(player.state.value.hasNext).isTrue()
        player.skipToNext()
        assertThat(player.state.value.currentTrack?.id).isEqualTo("a")
        player.cycleRepeatMode()
        assertThat(player.state.value.repeatMode).isEqualTo(MusicRepeatMode.One)
        player.cycleRepeatMode()
        assertThat(player.state.value.repeatMode).isEqualTo(MusicRepeatMode.Off)
    }

    @Test
    fun `shuffle visits every track once`() = runTest {
        val player = createPlayer()
        player.play("!room", tracks, startTrackId = "a")
        player.toggleShuffle()
        assertThat(player.state.value.shuffle).isTrue()
        // Порядок случайный: идём к началу перемешанного порядка, потом проходим его целиком.
        while (player.state.value.hasPrevious) player.skipToPrevious()
        val visited = mutableListOf(player.state.value.currentTrack!!.id)
        while (player.state.value.hasNext) {
            player.skipToNext()
            visited += player.state.value.currentTrack!!.id
        }
        assertThat(visited).containsExactly("a", "b", "c", "d")
        player.toggleShuffle()
        assertThat(player.state.value.shuffle).isFalse()
    }

    @Test
    fun `syncQueue adds loaded tracks without changing the current one`() = runTest {
        val player = createPlayer()
        player.play("!room", tracks.subList(1, 3), startTrackId = "c")
        player.syncQueue("!other", tracks)
        assertThat(player.state.value.queue).hasSize(2)
        player.syncQueue("!room", tracks)
        assertThat(player.state.value.queue.map { it.id }).containsExactly("a", "b", "c", "d").inOrder()
        assertThat(exoPlayer.mediaItemCount).isEqualTo(4)
        assertThat(player.state.value.currentTrack?.id).isEqualTo("c")
        assertThat(exoPlayer.getMediaItemAt(exoPlayer.currentMediaItemIndex).mediaId).isEqualTo("c")
    }

    @Test
    fun `tapping another track of the same queue only jumps to it`() = runTest {
        val player = createPlayer()
        player.play("!room", tracks, startTrackId = "a")
        player.toggleShuffle()
        player.play("!room", tracks, startTrackId = "c")
        assertThat(player.state.value.currentTrack?.id).isEqualTo("c")
        // Очередь та же — режимы не сбрасываются.
        assertThat(player.state.value.shuffle).isTrue()
    }

    @Test
    fun `media items carry the tags or the filename`() = runTest {
        val tagsStore = InMemoryTrackTagsStore(mapOf(tracks[0].mediaKey to MusicTrackTags(title = "Tagged", artist = "Band")))
        val player = createPlayer(tagsStore = tagsStore)
        runCurrent()
        player.play("!room", tracks, startTrackId = "a")
        assertThat(exoPlayer.getMediaItemAt(0).mediaMetadata.title.toString()).isEqualTo("Tagged")
        assertThat(exoPlayer.getMediaItemAt(0).mediaMetadata.artist.toString()).isEqualTo("Band")
        assertThat(exoPlayer.getMediaItemAt(1).mediaMetadata.title.toString()).isEqualTo("Song b")
        assertThat(exoPlayer.getMediaItemAt(1).mediaMetadata.artist.toString()).isEqualTo("Artist")
    }

    @Test
    fun `a saved position of a long track is restored`() = runTest {
        val longTrack = aMusicTrack(id = "mix", durationMs = 60 * 60 * 1000L)
        val positions = InMemoryTrackPositionStore().apply { positions[longTrack.mediaKey] = 600_000L }
        val player = createPlayer(positionStore = positions)
        player.play("!room", listOf(tracks[0], longTrack), startTrackId = "mix")
        assertThat(exoPlayer.currentPosition).isEqualTo(600_000L)
    }

    @Test
    fun `a voice message pauses the music and music resumes after it`() = runTest {
        val player = createPlayer()
        player.play("!room", tracks, startTrackId = "a")
        runCurrent()
        AudiblePlaybackController.requestFocus("\$voice")
        runCurrent()
        assertThat(exoPlayer.playWhenReady).isFalse()
        AudiblePlaybackController.release("\$voice")
        runCurrent()
        // Небольшая задержка: следующее голосовое могло начаться сразу.
        assertThat(exoPlayer.playWhenReady).isFalse()
        advanceTimeBy(1_000)
        assertThat(exoPlayer.playWhenReady).isTrue()
    }

    @Test
    fun `music paused by the user stays paused after a voice message`() = runTest {
        val player = createPlayer()
        player.play("!room", tracks, startTrackId = "a")
        player.pause()
        runCurrent()
        AudiblePlaybackController.requestFocus("\$voice")
        runCurrent()
        AudiblePlaybackController.release("\$voice")
        advanceTimeBy(1_000)
        assertThat(exoPlayer.playWhenReady).isFalse()
    }

    @Test
    fun `stop clears the queue and disconnects the service`() = runTest {
        val connector = FakeMusicServiceConnector()
        val player = createPlayer(connector = connector)
        player.play("!room", tracks, startTrackId = "a")
        player.stop()
        assertThat(player.state.value.isActive).isFalse()
        assertThat(exoPlayer.mediaItemCount).isEqualTo(0)
        assertThat(connector.isConnected).isFalse()
        assertThat(AudiblePlaybackController.current.value).isNull()
    }

    private fun TestScope.createPlayer(
        connector: MusicServiceConnector = FakeMusicServiceConnector(),
        positionStore: TrackPositionStore = InMemoryTrackPositionStore(),
        tagsStore: TrackTagsStore = InMemoryTrackTagsStore(),
    ): DefaultMusicPlayer {
        val context = RuntimeEnvironment.getApplication()
        val dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true)
        val offlineStore = MusicOfflineStore(context, FakeMatrixClientProvider(), dispatchers, backgroundScope)
        return DefaultMusicPlayer(
            playerFactory = { ExoPlayer.Builder(context).build().also { exoPlayer = it } },
            serviceConnector = connector,
            registry = MusicTrackRegistry(),
            resolver = MusicTrackResolver(
                matrixClientProvider = FakeMatrixClientProvider(),
                tagsExtractor = MusicTagsExtractor(context),
                tagsStore = tagsStore,
                offlineStore = offlineStore,
                dispatchers = dispatchers,
                appCoroutineScope = backgroundScope,
            ),
            positionStore = positionStore,
            tagsStore = tagsStore,
            offlineStore = offlineStore,
            dispatchers = dispatchers,
            appCoroutineScope = backgroundScope,
        )
    }
}
