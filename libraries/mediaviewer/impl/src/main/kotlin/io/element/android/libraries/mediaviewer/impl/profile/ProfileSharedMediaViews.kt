/*
 * Правка форка: строки и сетка вкладок общих медиа в профиле (`SharedMediaLayout`,
 * `SharedDocumentCell`, `SharedAudioCell`, `SharedLinkCell` TG).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.mediaviewer.impl.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.components.blurhash.blurHashBackground
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.matrix.ui.media.contentvalidation.collectOverallState
import io.element.android.libraries.mediaviewer.impl.gallery.di.MediaItemPresenterFactories
import io.element.android.libraries.mediaviewer.impl.gallery.di.rememberPresenter
import io.element.android.libraries.mediaviewer.impl.model.GroupedMediaItems
import io.element.android.libraries.mediaviewer.impl.model.MediaItem
import io.element.android.libraries.mediaviewer.impl.model.id
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.voiceplayer.api.VoiceMessageEvent
import io.element.android.libraries.voiceplayer.api.VoiceMessageState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** Поля карточек — как у инфо-карточки профиля (`TgProfileDefaults.sidePadding`). */
private val CardSidePadding = 14.dp
private val CardRadius = 16.dp

/** Превью в сетке крупнее, чем в галерее Element: плитка — треть ширины экрана. */
private const val GRID_THUMBNAIL_SIZE = 360L

internal fun LazyListScope.mediaGrid(
    media: AsyncData<GroupedMediaItems>,
    onOpen: (MediaItem.Event) -> Unit,
    onLoadMore: () -> Unit,
) {
    val items = media.itemsOrPlaceholder(this, emptyText = CommonStrings.larpgram_profile_empty_media) { it.imageAndVideoItems } ?: return
    val events = items.filter { it is MediaItem.Image || it is MediaItem.Video }.filterIsInstance<MediaItem.Event>()
    val loader = items.backwardLoader()
    if (events.isEmpty() && loader == null) {
        item(key = "shared_media_empty") { EmptyTab(stringResource(CommonStrings.larpgram_profile_empty_media)) }
        return
    }
    events.chunked(GRID_COLUMNS).forEach { row ->
        item(key = "shared_media_row_${row.first().id().value}", contentType = "shared_media_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = GRID_GAP),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
            ) {
                row.forEach { item -> MediaCell(item = item, onClick = { onOpen(item) }) }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    loader?.let { loaderItem(key = "shared_media_loader", timestamp = it.timestamp, onLoadMore = onLoadMore) }
}

internal fun LazyListScope.fileList(
    media: AsyncData<GroupedMediaItems>,
    onOpen: (MediaItem.Event) -> Unit,
    onLoadMore: () -> Unit,
) {
    val items = media.itemsOrPlaceholder(this, emptyText = CommonStrings.larpgram_profile_empty_files) { it.fileItems } ?: return
    val files = items.filter { it is MediaItem.File || it is MediaItem.Audio }.filterIsInstance<MediaItem.Event>()
    val loader = items.backwardLoader()
    if (files.isEmpty() && loader == null) {
        item(key = "shared_files_empty") { EmptyTab(stringResource(CommonStrings.larpgram_profile_empty_files)) }
        return
    }
    files.forEachIndexed { index, file ->
        item(key = "shared_file_${file.id().value}", contentType = "shared_file") {
            CardRow(isFirst = index == 0, isLast = index == files.lastIndex && loader == null) {
                FileRow(item = file, onClick = { onOpen(file) })
            }
        }
    }
    loader?.let { loaderItem(key = "shared_files_loader", timestamp = it.timestamp, onLoadMore = onLoadMore) }
}

internal fun LazyListScope.voiceList(
    media: AsyncData<GroupedMediaItems>,
    presenterFactories: MediaItemPresenterFactories,
    onLoadMore: () -> Unit,
) {
    val items = media.itemsOrPlaceholder(this, emptyText = CommonStrings.larpgram_profile_empty_voice) { it.fileItems } ?: return
    val voices = items.filterIsInstance<MediaItem.Voice>()
    val loader = items.backwardLoader()
    if (voices.isEmpty() && loader == null) {
        item(key = "shared_voice_empty") { EmptyTab(stringResource(CommonStrings.larpgram_profile_empty_voice)) }
        return
    }
    voices.forEachIndexed { index, voice ->
        item(key = "shared_voice_${voice.id().value}", contentType = "shared_voice") {
            val presenter: Presenter<VoiceMessageState> = presenterFactories.rememberPresenter(voice)
            CardRow(isFirst = index == 0, isLast = index == voices.lastIndex && loader == null) {
                VoiceRow(item = voice, state = presenter.present())
            }
        }
    }
    loader?.let { loaderItem(key = "shared_voice_loader", timestamp = it.timestamp, onLoadMore = onLoadMore) }
}

internal fun LazyListScope.linkList(
    links: ProfileLinksState,
    onLoadMore: () -> Unit,
) {
    if (links.isFailed || (links.items.isEmpty() && links.loaderTimestamp == null)) {
        item(key = "shared_links_empty") { EmptyTab(stringResource(CommonStrings.larpgram_profile_empty_links)) }
        return
    }
    links.items.forEachIndexed { index, link ->
        item(key = "shared_link_${link.id.value}", contentType = "shared_link") {
            CardRow(isFirst = index == 0, isLast = index == links.items.lastIndex && links.loaderTimestamp == null) {
                LinkRow(item = link)
            }
        }
    }
    // Текстовых сообщений без ссылок много: лента догружается, пока индикатор виден.
    links.loaderTimestamp?.let { loaderItem(key = "shared_links_loader", timestamp = it, onLoadMore = onLoadMore) }
}

private const val GRID_COLUMNS = 3
private val GRID_GAP = 2.dp

/** Пока лента не открылась — индикатор, если не открылась — пустая вкладка. */
private fun AsyncData<GroupedMediaItems>.itemsOrPlaceholder(
    scope: LazyListScope,
    emptyText: Int,
    select: (GroupedMediaItems) -> List<MediaItem>,
): List<MediaItem>? {
    return when (this) {
        is AsyncData.Success -> select(data)
        is AsyncData.Failure -> {
            scope.item(key = "shared_failed") { EmptyTab(stringResource(emptyText)) }
            null
        }
        else -> {
            scope.item(key = "shared_loading") { Loader() }
            null
        }
    }
}

private fun List<MediaItem>.backwardLoader(): MediaItem.LoadingIndicator? =
    filterIsInstance<MediaItem.LoadingIndicator>().firstOrNull { it.direction == Timeline.PaginationDirection.BACKWARDS }

private fun LazyListScope.loaderItem(key: String, timestamp: Long, onLoadMore: () -> Unit) {
    item(key = key) {
        val latestOnLoadMore by rememberUpdatedState(onLoadMore)
        Loader()
        // Пока индикатор виден, просим ещё: запрос во время идущей подгрузки лента отклоняет, а
        // новой эмиссии (и нового timestamp) может не быть, если подгрузка ничего не нашла.
        LaunchedEffect(timestamp) {
            while (true) {
                latestOnLoadMore()
                delay(LOAD_MORE_RETRY_INTERVAL)
            }
        }
    }
}

@Composable
private fun Loader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyTab(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RowScope.MediaCell(item: MediaItem.Event, onClick: () -> Unit) {
    val validation by item.validationState.collectOverallState()
    val (source, blurHash, duration) = when (item) {
        is MediaItem.Image -> Triple(item.thumbnailSource ?: item.mediaSource, item.blurHash, null)
        is MediaItem.Video -> Triple(item.thumbnailSource ?: item.mediaSource, item.blurHash, item.mediaInfo.duration)
        else -> Triple(null, null, null)
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .blurHashBackground(blurHash)
            .clickable(onClick = onClick),
    ) {
        when {
            validation.isInvalid() -> Icon(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center),
                imageVector = CompoundIcons.Error(),
                tint = ElementTheme.colors.iconCriticalPrimary,
                contentDescription = null,
            )
            !validation.isValidated() -> CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.Center),
                strokeWidth = 2.dp,
            )
            else -> AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = MediaRequestData(source, MediaRequestData.Kind.Thumbnail(GRID_THUMBNAIL_SIZE)),
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        }
        if (item is MediaItem.Video) {
            // Плашка длительности, как у видео в сетке TG.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.size(12.dp),
                    imageVector = CompoundIcons.PlaySolid(),
                    tint = Color.White,
                    contentDescription = null,
                )
                if (duration != null) {
                    Spacer(Modifier.width(2.dp))
                    Text(text = duration, style = ElementTheme.typography.fontBodyXsMedium, color = Color.White)
                }
            }
        }
    }
}

/** Строка внутри общей карточки: скругляются только верх первой и низ последней. */
@Composable
private fun CardRow(
    isFirst: Boolean,
    isLast: Boolean,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (isFirst) CardRadius else 0.dp,
        topEnd = if (isFirst) CardRadius else 0.dp,
        bottomStart = if (isLast) CardRadius else 0.dp,
        bottomEnd = if (isLast) CardRadius else 0.dp,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardSidePadding)
            .clip(shape)
            .background(ElementTheme.colors.bgSubtleSecondary),
    ) {
        content()
        if (!isLast) {
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}

@Composable
private fun FileRow(item: MediaItem.Event, onClick: () -> Unit) {
    val info = when (item) {
        is MediaItem.File -> item.mediaInfo
        is MediaItem.Audio -> item.mediaInfo
        else -> return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(fileColor(info.fileExtension, isAudio = item is MediaItem.Audio)),
            contentAlignment = Alignment.Center,
        ) {
            if (item is MediaItem.Audio) {
                Icon(imageVector = CompoundIcons.Audio(), tint = Color.White, contentDescription = null)
            } else {
                Text(
                    text = info.fileExtension.uppercase().take(4).ifEmpty { "FILE" },
                    style = ElementTheme.typography.fontBodySmMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.filename,
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                text = listOfNotNull(info.formattedFileSize.ifEmpty { null }, info.dateSent).joinToString(" · "),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/** Цвет плитки файла по расширению, как у значков документов TG. */
private fun fileColor(extension: String, isAudio: Boolean): Color {
    if (isAudio) return Color(0xFF8E64E8)
    return when (extension.lowercase()) {
        "pdf", "ppt", "pptx", "key" -> Color(0xFFE5574F)
        "xls", "xlsx", "csv", "ods", "numbers" -> Color(0xFF4CAF50)
        "zip", "rar", "7z", "tar", "gz", "apk" -> Color(0xFFF3A33B)
        else -> Color(0xFF3F8FE0)
    }
}

@Composable
private fun VoiceRow(item: MediaItem.Voice, state: VoiceMessageState) {
    val playPause = { state.eventSink(VoiceMessageEvent.PlayPause) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = playPause)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ElementTheme.colors.iconAccentPrimary),
            contentAlignment = Alignment.Center,
        ) {
            when (state.buttonType) {
                VoiceMessageState.ButtonType.Downloading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                VoiceMessageState.ButtonType.Pause -> Icon(imageVector = CompoundIcons.PauseSolid(), tint = Color.White, contentDescription = null)
                VoiceMessageState.ButtonType.Retry -> Icon(imageVector = CompoundIcons.Restart(), tint = Color.White, contentDescription = null)
                VoiceMessageState.ButtonType.Play,
                VoiceMessageState.ButtonType.Disabled -> Icon(imageVector = CompoundIcons.PlaySolid(), tint = Color.White, contentDescription = null)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.mediaInfo.senderName ?: item.mediaInfo.senderId?.value.orEmpty(),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val time = if (state.progress > 0f) state.time else item.mediaInfo.duration ?: state.time
            Text(
                text = listOfNotNull(item.mediaInfo.dateSent, time).joinToString(" · "),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LinkRow(item: ProfileLinkItem) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { runCatching { uriHandler.openUri(item.urls.first()) } }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ElementTheme.colors.textPrimary.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.title.take(1).uppercase(),
                style = ElementTheme.typography.fontHeadingSmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.text?.let { text ->
                Text(
                    text = text,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.urls.take(MAX_URLS_PER_ROW).forEach { url ->
                Text(
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri(url) } },
                    text = url,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textLinkExternal,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.dateSent,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

private const val MAX_URLS_PER_ROW = 3
private val LOAD_MORE_RETRY_INTERVAL = 1.seconds
