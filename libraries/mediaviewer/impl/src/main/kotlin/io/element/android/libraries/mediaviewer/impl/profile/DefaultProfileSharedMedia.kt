/*
 * Правка форка: вкладки общих медиа в профиле чата (`SharedMediaLayout` TG).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.mediaviewer.impl.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.features.contentscanner.api.ContentScannerService
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.mediaviewer.api.MediaViewerEntryPoint
import io.element.android.libraries.mediaviewer.api.ProfileSharedMedia
import io.element.android.libraries.mediaviewer.api.ProfileSharedMediaSection
import io.element.android.libraries.mediaviewer.api.ProfileSharedMediaTab
import io.element.android.libraries.mediaviewer.impl.datasource.LiveMediaTimeline
import io.element.android.libraries.mediaviewer.impl.datasource.MediaItemsPostProcessor
import io.element.android.libraries.mediaviewer.impl.datasource.TimelineMediaGalleryDataSource
import io.element.android.libraries.mediaviewer.impl.datasource.TimelineMediaItemsFactory
import io.element.android.libraries.mediaviewer.impl.gallery.di.MediaItemPresenterFactories
import io.element.android.libraries.mediaviewer.impl.model.GroupedMediaItems
import io.element.android.libraries.mediaviewer.impl.model.MediaItem
import io.element.android.libraries.mediaviewer.impl.model.blurHash
import io.element.android.libraries.mediaviewer.impl.model.eventId
import io.element.android.libraries.mediaviewer.impl.model.mediaInfo
import io.element.android.libraries.mediaviewer.impl.model.mediaSource
import io.element.android.libraries.mediaviewer.impl.model.thumbnailSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@ContributesBinding(RoomScope::class)
@Inject
class DefaultProfileSharedMedia(
    private val room: JoinedRoom,
    private val timelineMediaItemsFactory: TimelineMediaItemsFactory,
    private val mediaItemsPostProcessor: MediaItemsPostProcessor,
    private val mediaItemPresenterFactories: MediaItemPresenterFactories,
    private val contentScannerService: ContentScannerService,
    private val dateFormatter: DateFormatter,
    private val dispatchers: CoroutineDispatchers,
) : ProfileSharedMedia {
    @Composable
    override fun rememberSection(onOpenMedia: (MediaViewerEntryPoint.Params) -> Unit): ProfileSharedMediaSection {
        val scope = rememberCoroutineScope()
        // Своя лента медиа, а не общая для комнаты из галереи: та запускается один раз на RoomScope
        // и после закрытия экрана не перезапускается.
        val mediaDataSource = remember {
            TimelineMediaGalleryDataSource(
                room = room,
                mediaTimeline = LiveMediaTimeline(room),
                timelineMediaItemsFactory = timelineMediaItemsFactory,
                mediaItemsPostProcessor = mediaItemsPostProcessor,
            )
        }
        val linksDataSource = remember { ProfileLinksDataSource(room, dateFormatter, dispatchers) }
        LaunchedEffect(mediaDataSource) { mediaDataSource.start(this) }
        LaunchedEffect(linksDataSource) { linksDataSource.start(this) }

        val media by remember {
            mediaDataSource.groupedMediaItemsFlow()
                .onEach { data ->
                    if (data is AsyncData.Success) {
                        (data.data.imageAndVideoItems + data.data.fileItems)
                            .filterIsInstance<MediaItem.Event>()
                            .forEach { scope.validate(it) }
                    }
                }
        }.collectAsState(AsyncData.Uninitialized)
        val links by linksDataSource.state.collectAsState()
        val latestOnOpenMedia by rememberUpdatedState(onOpenMedia)

        return remember(media, links) {
            SectionImpl(
                media = media,
                links = links,
                presenterFactories = mediaItemPresenterFactories,
                onOpen = { item -> latestOnOpenMedia(item.toViewerParams()) },
                onLoadMoreMedia = {
                    scope.launch {
                        if (mediaDataSource.isReady) mediaDataSource.loadMore(Timeline.PaginationDirection.BACKWARDS)
                    }
                },
                onLoadMoreLinks = { scope.launch { linksDataSource.loadMore() } },
            )
        }
    }

    /** Как в галерее Element: без проверки сканером (или «всегда валидно») превью не показываются. */
    private fun CoroutineScope.validate(item: MediaItem.Event) {
        launch {
            val validationState = item.validationState
            val current = validationState.overallStateFlow.first()
            if (current.isLoading() || current.isValid()) return@launch
            contentScannerService.scan(listOfNotNull(item.thumbnailSource(), item.mediaSource()).distinct(), validationState)
        }
    }
}

private class SectionImpl(
    private val media: AsyncData<GroupedMediaItems>,
    private val links: ProfileLinksState,
    private val presenterFactories: MediaItemPresenterFactories,
    private val onOpen: (MediaItem.Event) -> Unit,
    private val onLoadMoreMedia: () -> Unit,
    private val onLoadMoreLinks: () -> Unit,
) : ProfileSharedMediaSection {
    override fun content(scope: LazyListScope, tab: ProfileSharedMediaTab) {
        when (tab) {
            ProfileSharedMediaTab.Media -> scope.mediaGrid(
                media = media,
                onOpen = onOpen,
                onLoadMore = onLoadMoreMedia,
            )
            ProfileSharedMediaTab.Files -> scope.fileList(
                media = media,
                onOpen = onOpen,
                onLoadMore = onLoadMoreMedia,
            )
            ProfileSharedMediaTab.Voice -> scope.voiceList(
                media = media,
                presenterFactories = presenterFactories,
                onLoadMore = onLoadMoreMedia,
            )
            ProfileSharedMediaTab.Links -> scope.linkList(
                links = links,
                onLoadMore = onLoadMoreLinks,
            )
        }
    }
}

private fun MediaItem.Event.toViewerParams(): MediaViewerEntryPoint.Params.RoomMedia {
    val mode = when (this) {
        is MediaItem.Image,
        is MediaItem.Video -> MediaViewerEntryPoint.MediaViewerMode.TimelineImagesAndVideos(Timeline.Mode.Media)
        is MediaItem.Audio,
        is MediaItem.Voice,
        is MediaItem.File -> MediaViewerEntryPoint.MediaViewerMode.TimelineFilesAndAudios(Timeline.Mode.Media)
    }
    return MediaViewerEntryPoint.Params.RoomMedia(
        mode = mode,
        eventId = eventId(),
        mediaInfo = mediaInfo(),
        mediaSource = mediaSource(),
        thumbnailSource = thumbnailSource(),
        blurHash = blurHash(),
    )
}
