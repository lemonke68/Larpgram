/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.music

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.mediaviewer.api.RoomMusicPlayback
import io.element.android.libraries.mediaviewer.impl.datasource.EventItemFactory
import io.element.android.libraries.mediaviewer.impl.datasource.LiveMediaTimeline
import io.element.android.libraries.mediaviewer.impl.datasource.MediaItemsPostProcessor
import io.element.android.libraries.mediaviewer.impl.datasource.TimelineMediaGalleryDataSource
import io.element.android.libraries.mediaviewer.impl.datasource.TimelineMediaItemsFactory
import io.element.android.libraries.mediaviewer.impl.datasource.VirtualItemFactory
import io.element.android.libraries.mediaviewer.impl.model.MediaItem
import io.element.android.libraries.musicplayer.api.MusicPlayer
import io.element.android.libraries.musicplayer.api.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Музыка комнаты из ленты чата. Трек начинает играть сразу, а очередь собирается следом: своя
 * лента медиа комнаты листается назад и каждую порцию аудио добавляет в играющую очередь, не
 * прерывая звук. Лента закрывается, когда история кончилась, очередь сменилась или чат закрыли.
 */
@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class)
class DefaultRoomMusicPlayback(
    private val room: JoinedRoom,
    private val musicPlayer: MusicPlayer,
    private val dispatchers: CoroutineDispatchers,
    private val virtualItemFactory: VirtualItemFactory,
    private val eventItemFactory: EventItemFactory,
    private val mediaItemsPostProcessor: MediaItemsPostProcessor,
) : RoomMusicPlayback {
    private var loaderJob: Job? = null

    override fun playFromTimeline(
        eventId: EventId?,
        mediaSource: MediaSource,
        filename: String,
        mimeType: String?,
        fileSize: Long?,
        durationMs: Long?,
    ) {
        val track = MusicTrack(
            id = musicTrackId(eventId, mediaSource),
            sessionId = room.sessionId,
            roomId = room.roomId,
            eventId = eventId,
            mediaSource = mediaSource,
            filename = filename,
            mimeType = mimeType,
            fileSize = fileSize,
            durationMs = durationMs?.takeIf { it > 0 },
        )
        val queueId = musicQueueId(room.roomId)
        val state = musicPlayer.state.value
        when {
            state.queueId == queueId && state.currentTrack?.id == track.id -> musicPlayer.playPause()
            state.queueId == queueId && state.queue.any { it.id == track.id } -> musicPlayer.play(queueId, state.queue, track.id)
            else -> {
                musicPlayer.play(queueId, listOf(track), track.id)
                loadQueue(queueId)
            }
        }
    }

    private fun loadQueue(queueId: String) {
        loaderJob?.cancel()
        loaderJob = room.roomCoroutineScope.launch {
            val sourceJob = Job(coroutineContext[Job])
            val dataSource = TimelineMediaGalleryDataSource(
                room = room,
                mediaTimeline = LiveMediaTimeline(room),
                timelineMediaItemsFactory = TimelineMediaItemsFactory(dispatchers, virtualItemFactory, eventItemFactory),
                mediaItemsPostProcessor = mediaItemsPostProcessor,
            )
            try {
                dataSource.start(CoroutineScope(coroutineContext + sourceJob))
                launch(sourceJob) {
                    dataSource.groupedMediaItemsFlow().collect { data ->
                        val tracks = data.dataOrNull()?.fileItems?.toMusicTracks(room.sessionId, room.roomId).orEmpty()
                        if (tracks.isNotEmpty()) {
                            withContext(dispatchers.main) {
                                if (musicPlayer.state.value.queueId == queueId) musicPlayer.syncQueue(queueId, tracks)
                            }
                        }
                    }
                }
                repeat(MAX_PAGES) {
                    if (musicPlayer.state.value.queueId != queueId) return@launch
                    if (!waitUntilReady(dataSource)) return@launch
                    dataSource.loadMore(Timeline.PaginationDirection.BACKWARDS)
                    // Индикатор загрузки назад пропал — история кончилась.
                    val hasMore = dataSource.getLastData().dataOrNull()?.fileItems?.any {
                        it is MediaItem.LoadingIndicator && it.direction == Timeline.PaginationDirection.BACKWARDS
                    } ?: true
                    if (!hasMore) {
                        // Последняя порция ещё может быть в пути.
                        delay(SETTLE_DELAY_MS)
                        return@launch
                    }
                }
            } catch (e: IllegalStateException) {
                Timber.w(e, "Music: could not load the room queue")
            } finally {
                sourceJob.cancel()
            }
        }
    }

    private suspend fun waitUntilReady(dataSource: TimelineMediaGalleryDataSource): Boolean {
        if (dataSource.isReady) return true
        return withTimeoutOrNull(READY_TIMEOUT_MS) {
            dataSource.groupedMediaItemsFlow().filter { dataSource.isReady }.first()
            true
        } ?: false
    }

    private companion object {
        /** Страниц медиа-ленты назад; хватает на сотни треков, дальше — догрузка во вкладке «Музыка». */
        const val MAX_PAGES = 30
        const val READY_TIMEOUT_MS = 10_000L
        const val SETTLE_DELAY_MS = 500L
    }
}
