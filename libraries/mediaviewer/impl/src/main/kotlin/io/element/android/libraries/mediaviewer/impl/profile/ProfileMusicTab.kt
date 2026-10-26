/*
 * Copyright (c) 2026 Larpgram.
 * Вкладка «Музыка» профиля чата (`SharedAudioCell` TG): треки комнаты с тегами и обложками,
 * поиск, тап — играть очередь комнаты с этого трека.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.SearchField
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.mediaviewer.impl.model.GroupedMediaItems
import io.element.android.libraries.mediaviewer.impl.music.filterMusic
import io.element.android.libraries.musicplayer.api.MusicPlayer
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import io.element.android.libraries.musicplayer.api.displayArtist
import io.element.android.libraries.musicplayer.api.displayTitle
import io.element.android.libraries.ui.strings.CommonStrings
import java.io.File

/**
 * [tracks] — вся музыка комнаты в порядке ленты; показываем новые сверху, как остальные вкладки.
 * Пока внизу виден индикатор, лента догружается сама: музыки среди файлов мало, а поиск должен
 * идти по всей истории.
 */
internal fun LazyListScope.musicList(
    media: AsyncData<GroupedMediaItems>,
    tracks: List<MusicTrack>,
    tags: Map<String, MusicTrackTags>,
    query: TextFieldState,
    musicPlayer: MusicPlayer,
    onPlay: (MusicTrack) -> Unit,
    onLoadMore: () -> Unit,
) {
    val items = media.itemsOrPlaceholder(this, emptyText = CommonStrings.larpgram_profile_empty_music) { it.fileItems } ?: return
    val loader = items.backwardLoader()
    if (tracks.isEmpty() && loader == null) {
        item(key = "shared_music_empty") { EmptyTab(stringResource(CommonStrings.larpgram_profile_empty_music)) }
        return
    }
    item(key = "shared_music_search") {
        SearchField(
            state = query,
            placeholder = stringResource(CommonStrings.larpgram_profile_music_search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
    // Чтение text здесь подписывает список на ввод: LazyColumn пересоберёт строки.
    val shown = tracks.filterMusic(query.text.toString(), tags).asReversed()
    if (shown.isEmpty() && loader == null) {
        item(key = "shared_music_not_found") { EmptyTab(stringResource(CommonStrings.larpgram_profile_music_not_found)) }
    }
    shown.forEachIndexed { index, track ->
        item(key = "shared_music_${track.id}", contentType = "shared_music") {
            CardRow(isFirst = index == 0, isLast = index == shown.lastIndex && loader == null) {
                MusicRow(track = track, tags = tags[track.mediaKey], musicPlayer = musicPlayer, onClick = { onPlay(track) })
            }
        }
    }
    loader?.let { loaderItem(key = "shared_music_loader", timestamp = it.timestamp, onLoadMore = onLoadMore) }
}

@Composable
private fun MusicRow(
    track: MusicTrack,
    tags: MusicTrackTags?,
    musicPlayer: MusicPlayer,
    onClick: () -> Unit,
) {
    val state by musicPlayer.state.collectAsState()
    val isCurrent = state.currentTrack?.id == track.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { if (isCurrent) musicPlayer.playPause() else onClick() })
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(fileColor(extension = "", isAudio = true)),
            contentAlignment = Alignment.Center,
        ) {
            tags?.artworkPath?.let { path ->
                AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            val showPlayState = isCurrent || tags?.artworkPath == null
            if (showPlayState) {
                if (tags?.artworkPath != null) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                }
                Icon(
                    imageVector = when {
                        isCurrent && (state.isPlaying || state.isBuffering) -> CompoundIcons.PauseSolid()
                        isCurrent -> CompoundIcons.PlaySolid()
                        else -> CompoundIcons.Audio()
                    },
                    tint = Color.White,
                    contentDescription = null,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.displayTitle(tags),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = if (isCurrent) ElementTheme.colors.textActionAccent else ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(track.displayArtist(tags), tags?.album).joinToString(" — ").ifEmpty { track.filename }
            Text(
                text = subtitle,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
