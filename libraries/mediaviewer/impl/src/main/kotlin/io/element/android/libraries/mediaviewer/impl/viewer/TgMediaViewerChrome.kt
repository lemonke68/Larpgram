/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.viewer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeImage
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeVideo
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.mediaviewer.impl.R
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList

/**
 * Правка форка: место текущего медиа «N из M» в чате, как в `PhotoViewer` Telegram (считаем от самого
 * старого). Пока лента догружается (в списке есть страницы загрузки), общего числа не знаем и
 * счётчик не показываем — лучше ничего, чем «3 из 5», которое через секунду станет «9 из 40».
 */
internal fun tgMediaPosition(listData: List<MediaViewerPageData>, currentIndex: Int): Pair<Int, Int>? {
    if (listData.size < 2 || currentIndex !in listData.indices) return null
    if (listData.any { it !is MediaViewerPageData.MediaViewerData }) return null
    return (listData.size - currentIndex) to listData.size
}

/** Лента миниатюр нужна только для фото и видео; у файлов и голосовых превью нет. */
internal fun tgShowThumbStrip(listData: List<MediaViewerPageData>): Boolean {
    val media = listData.filterIsInstance<MediaViewerPageData.MediaViewerData>()
    return media.size > 1 && media.all { it.mediaInfo.mimeType.isMimeTypeImage() || it.mediaInfo.mimeType.isMimeTypeVideo() }
}

/**
 * Правка форка: шапка просмотрщика Telegram — затемнение сверху вместо плашки, стрелка, имя и дата,
 * действия в меню ⋮, под шапкой счётчик «N из M».
 */
@Composable
internal fun TgMediaViewerTopBar(
    data: MediaViewerPageData.MediaViewerData,
    position: Pair<Int, Int>?,
    canShowInfo: Boolean,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val downloadedMedia by data.downloadedMedia
    val actionsEnabled = downloadedMedia.isSuccess()
    val senderName = data.mediaInfo.senderName
    val dateSent = data.mediaInfo.dateSent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBackClick)
            if (senderName != null && dateSent != null) {
                val description = stringResource(CommonStrings.a11y_sent_by_sender_at_date, senderName, dateSent)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .clearAndSetSemantics {
                            heading()
                            contentDescription = description
                        },
                ) {
                    Text(
                        text = senderName,
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = dateSent,
                        style = TextStyle(fontSize = 14.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = CompoundIcons.OverflowVertical(),
                        contentDescription = stringResource(CommonStrings.action_open_context_menu),
                        tint = Color.White,
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.larpgram_viewer_save)) },
                        leadingIcon = { Icon(CompoundIcons.Download(), contentDescription = null) },
                        enabled = actionsEnabled,
                        onClick = {
                            showMenu = false
                            onSaveClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.larpgram_viewer_share)) },
                        leadingIcon = { Icon(CompoundIcons.ShareAndroid(), contentDescription = null) },
                        enabled = actionsEnabled,
                        onClick = {
                            showMenu = false
                            onShareClick()
                        },
                    )
                    if (canShowInfo) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.larpgram_viewer_info)) },
                            leadingIcon = { Icon(CompoundIcons.Info(), contentDescription = null) },
                            enabled = actionsEnabled,
                            onClick = {
                                showMenu = false
                                onInfoClick()
                            },
                        )
                    }
                }
            }
        }
        if (position != null) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.larpgram_viewer_counter, position.first, position.second),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Правка форка: лента миниатюр снизу (`GroupedPhotosListView` Telegram: 42×56dp, зазор 1dp).
 * Текущая миниатюра шире и по центру; тап — перейти к этому медиа. Порядок как у пейджера: новые
 * справа.
 */
@Composable
internal fun TgMediaViewerThumbStrip(
    listData: ImmutableList<MediaViewerPageData>,
    currentIndex: Int,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(TG_THUMB_STRIP_HEIGHT),
    ) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
        LaunchedEffect(currentIndex) {
            if (currentIndex in listData.indices) listState.animateScrollToItem(currentIndex)
        }
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = ((maxWidth - THUMB_CURRENT_WIDTH) / 2).coerceAtLeast(0.dp)),
        ) {
            itemsIndexed(listData, key = { _, page -> page.pagerKey }) { index, page ->
                val isCurrent = index == currentIndex
                val width by animateDpAsState(if (isCurrent) THUMB_CURRENT_WIDTH else THUMB_WIDTH, label = "thumb_width")
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(THUMB_HEIGHT)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x33FFFFFF))
                        .then(if (isCurrent) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(2.dp)) else Modifier)
                        .clickable { onClick(index) },
                ) {
                    val data = page as? MediaViewerPageData.MediaViewerData
                    if (data != null) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            model = MediaRequestData(data.thumbnailSource ?: data.mediaSource, MediaRequestData.Kind.Thumbnail(100)),
                            contentScale = ContentScale.Crop,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

internal val TG_THUMB_STRIP_HEIGHT = 64.dp
private val THUMB_WIDTH = 42.dp
private val THUMB_CURRENT_WIDTH = 56.dp
private val THUMB_HEIGHT = 56.dp
