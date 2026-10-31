/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaplayer.impl

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class LazySimplePlayerTest {
    @Test
    fun `idle room and disposal never create a player`() {
        val player = LazySimplePlayer { error("Text-only room must not create a player") }
        player.addListener(RecordingListener())
        assertThat(player.currentPosition).isEqualTo(0L)
        assertThat(player.duration).isEqualTo(C.TIME_UNSET)
        assertThat(player.playbackState).isEqualTo(Player.STATE_IDLE)
        assertThat(player.isPlaying()).isFalse()
        assertThat(player.getCurrentMediaItem()).isNull()
        player.pause()
        player.clearMediaItems()
        player.seekTo(0)
        player.setPlaybackSpeed(1.5f)
        player.release()
        player.release()
        assertThrows(IllegalStateException::class.java) { player.play() }
    }

    @Test
    fun `saved voice speed is applied only when media is loaded`() {
        var speed = 0f
        val player = LazySimplePlayer {
            FakeSimplePlayer(
                setMediaItemLambda = { _, _ -> },
                setPlaybackSpeedLambda = { speed = it },
            )
        }
        player.setPlaybackSpeed(1.5f)
        assertThat(speed).isEqualTo(0f)
        player.setMediaItem(MediaItem.Builder().setMediaId("voice").build(), 0)
        assertThat(speed).isEqualTo(1.5f)
    }

    @Test
    fun `first media attaches listeners before callbacks and reuses the player`() {
        var created = 0
        var prepared = 0
        val earlyListener = RecordingListener()
        val lateListener = RecordingListener()
        val backend = FakeSimplePlayer(
            setMediaItemLambda = { _, _ -> },
            prepareLambda = { prepared++ },
        )
        val player = LazySimplePlayer {
            created++
            backend
        }
        player.addListener(earlyListener)
        assertThat(created).isEqualTo(0)
        val item = MediaItem.Builder().setMediaId("test-audio").build()
        player.setMediaItem(item, 42)
        backend.simulateMediaItemTransition(item)
        assertThat(earlyListener.mediaItem).isEqualTo(item)
        player.addListener(lateListener)
        backend.simulateIsPlayingChanged(true)
        assertThat(earlyListener.isPlaying).isTrue()
        assertThat(lateListener.isPlaying).isTrue()
        player.prepare()
        player.setMediaItem(item, 0)
        assertThat(created).isEqualTo(1)
        assertThat(prepared).isEqualTo(1)
    }

    @Test
    fun `active playback delegates controls and releases only once`() {
        var played = 0
        var paused = 0
        var released = 0
        var speed = 0f
        var position = 0L
        val player = LazySimplePlayer {
            FakeSimplePlayer(
                playLambda = { played++ },
                pauseLambda = { paused++ },
                setPlaybackSpeedLambda = { speed = it },
                seekToLambda = { position = it },
                releaseLambda = { released++ },
            )
        }
        player.play()
        player.pause()
        player.setPlaybackSpeed(1.5f)
        player.seekTo(100)
        player.release()
        player.release()
        assertThat(played).isEqualTo(1)
        assertThat(paused).isEqualTo(1)
        assertThat(speed).isEqualTo(1.5f)
        assertThat(position).isEqualTo(100L)
        assertThat(released).isEqualTo(1)
    }

    private class RecordingListener : SimplePlayer.Listener {
        var mediaItem: MediaItem? = null
        var isPlaying = false
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this.isPlaying = isPlaying
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?) {
            this.mediaItem = mediaItem
        }
        override fun onPlaybackStateChanged(playbackState: Int) = Unit
    }
}
