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

/** Keeps opening a text-only room from allocating an audio player and its playback thread. */
class LazySimplePlayer(private val factory: () -> SimplePlayer) : SimplePlayer {
    private var delegate: SimplePlayer? = null
    private val listeners = mutableListOf<SimplePlayer.Listener>()
    private var released = false
    private var playbackSpeed: Float? = null

    private fun player(): SimplePlayer {
        check(!released) { "Player is released" }
        return delegate ?: factory().also { player ->
            delegate = player
            playbackSpeed?.let(player::setPlaybackSpeed)
            listeners.forEach(player::addListener)
            listeners.clear()
        }
    }

    override fun addListener(listener: SimplePlayer.Listener) {
        check(!released) { "Player is released" }
        delegate?.addListener(listener) ?: run { listeners.add(listener) }
    }

    override val currentPosition: Long get() = delegate?.currentPosition ?: 0L
    override val playbackState: Int get() = delegate?.playbackState ?: Player.STATE_IDLE
    override val duration: Long get() = delegate?.duration ?: C.TIME_UNSET

    override fun clearMediaItems() {
        delegate?.clearMediaItems()
    }
    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) = player().setMediaItem(mediaItem, startPositionMs)
    override fun getCurrentMediaItem(): MediaItem? = delegate?.getCurrentMediaItem()
    override fun prepare() = player().prepare()
    override fun play() = player().play()
    override fun isPlaying(): Boolean = delegate?.isPlaying() ?: false
    override fun pause() {
        delegate?.pause()
    }
    override fun seekTo(positionMs: Long) {
        delegate?.seekTo(positionMs)
    }
    override fun setPlaybackSpeed(speed: Float) {
        check(!released) { "Player is released" }
        playbackSpeed = speed
        delegate?.setPlaybackSpeed(speed)
    }

    override fun release() {
        if (released) return
        released = true
        listeners.clear()
        delegate?.release()
        delegate = null
    }
}
