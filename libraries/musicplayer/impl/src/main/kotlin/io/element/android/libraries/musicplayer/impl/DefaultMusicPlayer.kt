/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.media.AudiblePlaybackController
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.musicplayer.api.MusicPlayer
import io.element.android.libraries.musicplayer.api.MusicPlayerState
import io.element.android.libraries.musicplayer.api.MusicRepeatMode
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import io.element.android.libraries.musicplayer.api.displayArtist
import io.element.android.libraries.musicplayer.api.displayTitle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/** Токен музыки в [AudiblePlaybackController]. */
internal object MusicAudibleToken

/**
 * Общий плеер музыки. ExoPlayer живёт здесь, [MusicPlaybackService] оборачивает его в
 * MediaSession (уведомление, экран блокировки, гарнитура). Навигация по очереди, shuffle и
 * repeat — штатные у плейлиста ExoPlayer, поэтому одинаково работают из приложения и снаружи.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultMusicPlayer(
    private val playerFactory: MusicPlayerFactory,
    private val serviceConnector: MusicServiceConnector,
    private val registry: MusicTrackRegistry,
    private val resolver: MusicTrackResolver,
    private val positionStore: TrackPositionStore,
    private val tagsStore: TrackTagsStore,
    private val dispatchers: CoroutineDispatchers,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MusicPlayer {
    private val _state = MutableStateFlow(MusicPlayerState())
    override val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

    private val _tags = MutableStateFlow<ImmutableMap<String, MusicTrackTags>>(persistentMapOf())
    override val tags: StateFlow<ImmutableMap<String, MusicTrackTags>> = _tags.asStateFlow()

    private var player: Player? = null
    private var queueId: String? = null
    private var queue: List<MusicTrack> = emptyList()
        set(value) {
            field = value
            immutableQueue = value.toImmutableList()
        }
    private var immutableQueue: ImmutableList<MusicTrack> = persistentListOf()
    private var currentTrackId: String? = null
    private var lastPositionMs: Long = 0
    private var lastDurationMs: Long = 0
    private var consecutiveErrors = 0
    private var tickerJob: Job? = null
    private val arbiter = MusicFocusArbiter(MusicAudibleToken)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = handleTransition(reason)

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                consecutiveErrors = 0
                // Play мог прийти снаружи (уведомление, гарнитура): забираем «моно-звук».
                AudiblePlaybackController.requestFocus(MusicAudibleToken)
                startTicker()
            } else {
                tickerJob?.cancel()
                savePosition()
            }
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    init {
        appCoroutineScope.launch(dispatchers.io) {
            val stored = tagsStore.loadAll()
            resolver.markTagsKnown(stored.keys)
            _tags.update { (stored + it).toImmutableMap() }
        }
        appCoroutineScope.launch(dispatchers.main) {
            resolver.tagsResolved.collect { (key, tags) ->
                _tags.update { (it + (key to tags)).toImmutableMap() }
                refreshMetadata(key)
            }
        }
        appCoroutineScope.launch(dispatchers.main) {
            AudiblePlaybackController.current.collectLatest { current ->
                // Голосовые часто идут подряд: не включаем музыку в паузе между ними.
                if (current == null) delay(RESUME_DELAY_MS)
                val player = player ?: return@collectLatest
                when (arbiter.onAudibleChanged(current, isMusicPlaying = player.playWhenReady)) {
                    MusicFocusArbiter.Action.Pause -> player.pause()
                    MusicFocusArbiter.Action.Resume -> player.play()
                    MusicFocusArbiter.Action.None -> Unit
                }
            }
        }
    }

    /** Плеер для MediaSession сервиса. */
    fun sessionPlayer(): Player = ensurePlayer()

    /** Сервис ушёл. Если очередь пуста — плеер больше не нужен. */
    fun onServiceDestroyed() {
        if (queue.isEmpty()) {
            player?.removeListener(listener)
            player?.release()
            player = null
        }
    }

    /** Смахнули приложение из недавних: на паузе закрываемся, играющая музыка продолжает. */
    fun onTaskRemoved(): Boolean {
        val player = player
        val keepPlaying = player != null && player.playWhenReady && player.mediaItemCount > 0
        if (!keepPlaying) stop()
        return keepPlaying
    }

    override fun play(queueId: String, tracks: List<MusicTrack>, startTrackId: String) {
        if (tracks.isEmpty()) return
        arbiter.onUserAction()
        val player = ensurePlayer()
        registry.register(tracks)
        val existingIndex = queue.indexOfFirst { it.id == startTrackId }
        if (queueId == this.queueId && existingIndex >= 0) {
            syncQueue(queueId, tracks)
            val index = queue.indexOfFirst { it.id == startTrackId }
            if (index != player.currentMediaItemIndex) {
                player.seekTo(index, C.TIME_UNSET)
            } else if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(index, 0)
            }
        } else {
            savePosition()
            val built = MusicQueue.build(tracks, startTrackId)
            this.queueId = queueId
            queue = built.tracks
            currentTrackId = null
            player.setMediaItems(built.tracks.map(::toMediaItem), built.startIndex, C.TIME_UNSET)
        }
        startPlayback(player)
    }

    override fun syncQueue(queueId: String, tracks: List<MusicTrack>) {
        if (queueId != this.queueId) return
        val player = player ?: return
        val inserts = MusicQueue.inserts(queue.map { it.id }, tracks)
        if (inserts.isEmpty()) return
        registry.register(tracks)
        val updated = queue.toMutableList()
        inserts.forEach { insert ->
            updated.addAll(insert.index, insert.tracks)
            player.addMediaItems(insert.index, insert.tracks.map(::toMediaItem))
        }
        queue = updated
        publish()
    }

    override fun playPause() {
        val player = player ?: return
        arbiter.onUserAction()
        if (player.playWhenReady) {
            player.pause()
            publish()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(player.currentMediaItemIndex, 0)
            startPlayback(player)
        }
    }

    override fun pause() {
        arbiter.onUserAction()
        player?.pause()
        publish()
    }

    override fun skipToNext() {
        player?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
        publish()
    }

    override fun skipToPrevious() {
        // Как в плеерах: дальше 3 с от начала — в начало трека, иначе предыдущий.
        player?.seekToPrevious()
        publish()
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0))
        publish()
    }

    override fun toggleShuffle() {
        val player = player ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        publish()
    }

    override fun cycleRepeatMode() {
        val player = player ?: return
        player.repeatMode = MusicQueue.nextRepeatMode(player.repeatMode.toMusicRepeatMode()).toPlayerRepeatMode()
        publish()
    }

    override fun stop() {
        arbiter.onUserAction()
        savePosition()
        tickerJob?.cancel()
        player?.run {
            stop()
            clearMediaItems()
        }
        queue = emptyList()
        queueId = null
        currentTrackId = null
        AudiblePlaybackController.release(MusicAudibleToken)
        serviceConnector.disconnect()
        resolver.releaseAll()
        registry.clear()
        publish()
    }

    private fun startPlayback(player: Player) {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        serviceConnector.connect()
        AudiblePlaybackController.requestFocus(MusicAudibleToken)
        player.play()
        publish()
    }

    private fun ensurePlayer(): Player {
        return player ?: playerFactory.create().also {
            it.addListener(listener)
            player = it
        }
    }

    private fun toMediaItem(track: MusicTrack): MediaItem {
        val tags = _tags.value[track.mediaKey]
        val metadata = MediaMetadata.Builder()
            .setTitle(track.displayTitle(tags))
            .setArtist(track.displayArtist(tags))
            .setAlbumTitle(tags?.album)
            .setArtworkUri(tags?.artworkPath?.let { Uri.fromFile(File(it)) })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(MusicTrackRegistry.uriFor(track))
            .setMimeType(track.mimeType?.takeIf { it.startsWith("audio/") })
            .setMediaMetadata(metadata)
            .build()
    }

    /** Теги пришли после скачивания — обновляем уведомление и экран блокировки. */
    private fun refreshMetadata(mediaKey: String) {
        val player = player ?: return
        queue.forEachIndexed { index, track ->
            if (track.mediaKey == mediaKey && index < player.mediaItemCount) {
                player.replaceMediaItem(index, toMediaItem(track))
            }
        }
    }

    private fun handleTransition(reason: Int) {
        val player = player ?: return
        val previousId = currentTrackId
        val previous = previousId?.let { id -> queue.firstOrNull { it.id == id } }
        if (previous != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                positionStore.clear(previous.mediaKey)
            } else {
                saveTrackPosition(previous, lastPositionMs, lastDurationMs)
            }
        }
        val current = queue.getOrNull(player.currentMediaItemIndex)
        currentTrackId = current?.id
        lastPositionMs = 0
        lastDurationMs = current?.durationMs ?: 0
        if (current == null) return
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && player.currentPosition < PositionPolicy.MIN_POSITION_MS) {
            val restore = PositionPolicy.positionToRestore(positionStore.get(current.mediaKey), current.durationMs)
            if (restore > 0) player.seekTo(restore)
        }
        prefetchNext(player)
    }

    private fun prefetchNext(player: Player) {
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex != C.INDEX_UNSET) queue.getOrNull(nextIndex)?.let(resolver::prefetch)
    }

    private fun handleError(error: PlaybackException) {
        Timber.w(error, "Music: playback error")
        val player = player ?: return
        consecutiveErrors++
        // Битый или недоступный файл — к следующему, но не по кругу бесконечно.
        if (consecutiveErrors <= MAX_CONSECUTIVE_ERRORS && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
        }
        publish()
    }

    private fun savePosition() {
        val player = player ?: return
        val track = queue.getOrNull(player.currentMediaItemIndex) ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: track.durationMs ?: 0
        saveTrackPosition(track, player.currentPosition, duration)
    }

    private fun saveTrackPosition(track: MusicTrack, positionMs: Long, durationMs: Long) {
        val toSave = PositionPolicy.positionToSave(positionMs, durationMs)
        appCoroutineScope.launch(dispatchers.io) {
            if (toSave == null) positionStore.clear(track.mediaKey) else positionStore.set(track.mediaKey, toSave)
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = appCoroutineScope.launch(dispatchers.main) {
            var ticks = 0
            while (isActive) {
                publish()
                if (++ticks % SAVE_EVERY_TICKS == 0) savePosition()
                delay(TICK_MS)
            }
        }
    }

    private fun publish() {
        val player = player
        if (player == null || queue.isEmpty()) {
            _state.value = MusicPlayerState()
            return
        }
        val index = player.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val track = queue.getOrNull(index)
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: track?.durationMs ?: 0
        val position = player.currentPosition
        if (track != null && track.id == currentTrackId) {
            lastPositionMs = position
            lastDurationMs = duration
        }
        _state.value = MusicPlayerState(
            queueId = queueId,
            queue = immutableQueue,
            currentIndex = index,
            isPlaying = player.isPlaying,
            isBuffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            positionMs = position,
            durationMs = duration,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode.toMusicRepeatMode(),
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            hasError = player.playerError != null,
        )
    }

    private companion object {
        const val RESUME_DELAY_MS = 600L
        const val TICK_MS = 500L
        const val SAVE_EVERY_TICKS = 20
        const val MAX_CONSECUTIVE_ERRORS = 3
    }
}

internal fun Int.toMusicRepeatMode(): MusicRepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> MusicRepeatMode.All
    Player.REPEAT_MODE_ONE -> MusicRepeatMode.One
    else -> MusicRepeatMode.Off
}

internal fun MusicRepeatMode.toPlayerRepeatMode(): Int = when (this) {
    MusicRepeatMode.Off -> Player.REPEAT_MODE_OFF
    MusicRepeatMode.All -> Player.REPEAT_MODE_ALL
    MusicRepeatMode.One -> Player.REPEAT_MODE_ONE
}
