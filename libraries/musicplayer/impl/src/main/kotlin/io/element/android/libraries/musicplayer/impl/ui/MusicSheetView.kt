/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl.ui

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Slider
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.musicplayer.api.MusicOrder
import io.element.android.libraries.musicplayer.api.MusicRepeatMode
import io.element.android.libraries.musicplayer.impl.R
import kotlinx.collections.immutable.persistentListOf
import java.io.File

/**
 * Правка форка: шторка плеера Telegram (`AudioPlayerAlert`, в iOS — с карточкой трека снизу).
 * Сверху очередь (от новых к старым, играющий отмечен), снизу текущий трек с обложкой и
 * «поделиться», перемотка со временем и скоростью, кнопки порядок / назад / play / вперёд /
 * повтор и большая кнопка «Добавить в профиль».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicSheet(
    state: MusicSheetState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        scrollable = false,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS),
        containerColor = sheetColor(),
        tonalElevation = 0.dp,
    ) {
        MusicSheetContent(state = state)
    }
}

@Composable
internal fun ColumnScope.MusicSheetContent(state: MusicSheetState) {
    ProfileActionFeedback(state)
    if (state.title != null) {
        Text(
            text = state.title,
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp, vertical = 8.dp),
        )
    }
    TrackList(
        state = state,
        modifier = Modifier.weight(1f, fill = false),
    )
    PlayerPanel(state = state)
}

@Composable
private fun TrackList(state: MusicSheetState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val currentIndex = state.tracks.indexOfFirst { it.isCurrent }
    LaunchedEffect(currentIndex >= 0) {
        if (currentIndex > 0) listState.scrollToItem(currentIndex - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.heightIn(max = LIST_MAX_HEIGHT),
    ) {
        items(state.tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isPlaying = state.isPlaying,
                canRemove = state.canRemoveTracks && !track.isCurrent,
                onClick = { state.eventSink(MusicSheetEvent.Play(track.id)) },
                onRemove = { state.eventSink(MusicSheetEvent.RemoveFromProfile(track.id)) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: MusicSheetTrack,
    isPlaying: Boolean,
    canRemove: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuShown by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (canRemove) ({ menuShown = true }) else null,
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val accent = ElementTheme.colors.bgAccentRest
            Box(
                modifier = Modifier
                    .size(ROW_BUTTON_SIZE)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                if (track.artworkPath != null) {
                    AsyncImage(
                        model = File(track.artworkPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(ROW_BUTTON_SIZE),
                    )
                    Box(
                        modifier = Modifier
                            .size(ROW_BUTTON_SIZE)
                            .background(Color.Black.copy(alpha = 0.35f)),
                    )
                }
                Icon(
                    imageVector = if (track.isCurrent && isPlaying) CompoundIcons.PauseSolid() else CompoundIcons.PlaySolid(),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = track.title,
                    style = ElementTheme.typography.fontBodyLgMedium,
                    color = if (track.isCurrent) ElementTheme.colors.textActionAccent else ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                trackSubtitle(track)?.let { subtitle ->
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
        DropdownMenu(
            expanded = menuShown,
            onDismissRequest = { menuShown = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = ElementTheme.colors.bgCanvasDefault,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.larpgram_music_remove_from_profile), color = ElementTheme.colors.textCriticalPrimary) },
                leadingIcon = { Icon(CompoundIcons.Delete(), contentDescription = null, tint = ElementTheme.colors.iconCriticalPrimary) },
                onClick = {
                    menuShown = false
                    onRemove()
                },
            )
        }
    }
}

/** «3:12 • Артист», без длительности — «Артист» или размер файла, как в Telegram. */
@Composable
private fun trackSubtitle(track: MusicSheetTrack): String? {
    val context = LocalContext.current
    val duration = track.durationMs?.takeIf { it > 0 }?.let(::formatTime)
    val parts = listOfNotNull(duration, track.artist)
    return when {
        parts.isNotEmpty() -> parts.joinToString(" • ")
        track.fileSize != null -> Formatter.formatShortFileSize(context, track.fileSize)
        else -> null
    }
}

@Composable
private fun PlayerPanel(state: MusicSheetState) {
    val current = state.current ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(artworkPath = current.artworkPath)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = current.title,
                    style = ElementTheme.typography.fontBodyLgMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = current.artist ?: stringResource(R.string.larpgram_music_unknown_artist),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { state.eventSink(MusicSheetEvent.Share) }) {
                Icon(
                    imageVector = CompoundIcons.ShareAndroid(),
                    contentDescription = stringResource(R.string.larpgram_music_share),
                    tint = ElementTheme.colors.iconPrimary,
                )
            }
        }
        SeekRow(state = state)
        ControlsRow(state = state)
        ProfileButtonView(state = state)
    }
}

@Composable
private fun Cover(artworkPath: String?) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(COVER_SIZE)
            .clip(shape)
            .background(ElementTheme.colors.bgSubtleSecondary),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkPath != null) {
            AsyncImage(
                model = File(artworkPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(COVER_SIZE),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun SeekRow(state: MusicSheetState) {
    var seekingTo by remember { mutableFloatStateOf(-1f) }
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Slider(
            value = if (seekingTo >= 0) seekingTo else progress,
            onValueChange = { seekingTo = it },
            onValueChangeFinish = {
                if (seekingTo >= 0 && state.durationMs > 0) state.eventSink(MusicSheetEvent.Seek((seekingTo * state.durationMs).toLong()))
                seekingTo = -1f
            },
            enabled = state.durationMs > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shownPosition = if (seekingTo >= 0) (seekingTo * state.durationMs).toLong() else state.positionMs
            Text(
                text = formatTime(shownPosition),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            SpeedButton(speed = state.speed, onClick = { state.eventSink(MusicSheetEvent.CycleSpeed) })
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatTime(state.durationMs),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

/** «1X» в рамке, как у Telegram; не 1X — цветом акцента. */
@Composable
internal fun SpeedButton(
    speed: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDefault = speed in 0.95f..1.05f
    val color = if (isDefault) ElementTheme.colors.iconSecondary else ElementTheme.colors.textActionAccent
    val description = stringResource(R.string.larpgram_music_speed)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        Text(
            text = formatSpeed(speed),
            style = ElementTheme.typography.fontBodySmMedium.copy(fontSize = with(density) { 12.dp.toSp() }),
            color = color,
            modifier = Modifier
                .border(width = 1.5.dp, color = color, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun ControlsRow(state: MusicSheetState) {
    val active = ElementTheme.colors.textActionAccent
    val inactive = ElementTheme.colors.iconSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(
            icon = if (state.order == MusicOrder.Shuffle) Icons.Filled.Shuffle else Icons.Filled.SwapVert,
            description = stringResource(
                when (state.order) {
                    MusicOrder.Default -> R.string.larpgram_music_order_default
                    MusicOrder.Reversed -> R.string.larpgram_music_order_reversed
                    MusicOrder.Shuffle -> R.string.larpgram_music_shuffle
                }
            ),
            tint = if (state.order == MusicOrder.Default) inactive else active,
            onClick = { state.eventSink(MusicSheetEvent.CycleOrder) },
        )
        ControlButton(
            icon = Icons.Filled.FastRewind,
            description = stringResource(R.string.larpgram_music_previous),
            tint = ElementTheme.colors.iconPrimary,
            iconSize = 36.dp,
            onClick = { state.eventSink(MusicSheetEvent.Previous) },
        )
        ControlButton(
            icon = if (state.isPlaying) CompoundIcons.PauseSolid() else CompoundIcons.PlaySolid(),
            description = stringResource(if (state.isPlaying) R.string.larpgram_music_pause else R.string.larpgram_music_play),
            tint = ElementTheme.colors.iconPrimary,
            iconSize = 44.dp,
            buttonSize = 64.dp,
            onClick = { state.eventSink(MusicSheetEvent.PlayPause) },
        )
        ControlButton(
            icon = Icons.Filled.FastForward,
            description = stringResource(R.string.larpgram_music_next),
            tint = if (state.hasNext) ElementTheme.colors.iconPrimary else ElementTheme.colors.iconDisabled,
            iconSize = 36.dp,
            onClick = { state.eventSink(MusicSheetEvent.Next) },
        )
        ControlButton(
            icon = if (state.repeatMode == MusicRepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            description = stringResource(R.string.larpgram_music_repeat),
            tint = if (state.repeatMode == MusicRepeatMode.Off) inactive else active,
            onClick = { state.eventSink(MusicSheetEvent.CycleRepeat) },
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    iconSize: Dp = 26.dp,
    buttonSize: Dp = 48.dp,
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun ProfileButtonView(state: MusicSheetState) {
    if (state.profileButton == ProfileButton.Hidden) return
    val isAdd = state.profileButton == ProfileButton.Add
    val isLoading = state.profileAction is AsyncAction.Loading
    val container = if (isAdd) ElementTheme.colors.bgAccentRest else ElementTheme.colors.bgSubtleSecondary
    val content = if (isAdd) Color.White else ElementTheme.colors.textPrimary
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(PROFILE_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(PROFILE_BUTTON_HEIGHT / 2))
            .background(container)
            .clickable(enabled = !isLoading) { state.eventSink(MusicSheetEvent.ToggleProfile) },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = if (isAdd) CompoundIcons.UserProfileSolid() else CompoundIcons.Delete(),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(if (isAdd) R.string.larpgram_music_add_to_profile else R.string.larpgram_music_remove_from_profile),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = content,
            )
        }
    }
}

/** Итог «в профиль / из профиля» — короткой всплывашкой, шторка остаётся открытой. */
@Composable
private fun ProfileActionFeedback(state: MusicSheetState) {
    val context = LocalContext.current
    val action = state.profileAction
    LaunchedEffect(action) {
        val message = when (action) {
            is AsyncAction.Success -> if (action.data ==
                ProfileButton.Remove) {
                    R.string.larpgram_music_added_to_profile
                } else {
                    R.string.larpgram_music_removed_from_profile
                }
            is AsyncAction.Failure -> R.string.larpgram_music_profile_failed
            else -> null
        } ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        state.eventSink(MusicSheetEvent.ClearProfileAction)
    }
}

@Composable
internal fun sheetColor(): Color = if (ElementTheme.isLightTheme) Color.White else Color(0xFF1C1C1D)

internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** 1 → «1X», 1.5 → «1.5X». */
internal fun formatSpeed(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, speed)
    return "${text}X"
}

private val SHEET_RADIUS = 20.dp
private val LIST_MAX_HEIGHT = 360.dp
private val ROW_BUTTON_SIZE = 44.dp
private val COVER_SIZE = 48.dp
private val PROFILE_BUTTON_HEIGHT = 48.dp

@PreviewsDayNight
@Composable
internal fun MusicSheetContentPreview() = ElementPreview {
    val tracks = persistentListOf(
        MusicSheetTrack("a", "подожди мне звонят2", null, null, 19_400_000, null, isCurrent = true),
        MusicSheetTrack("b", "(emotion)2", "Cynthoni", 192_000, null, null, isCurrent = false),
        MusicSheetTrack("c", "Весна", "Unknown", 210_000, null, null, isCurrent = false),
    )
    Column(modifier = Modifier.background(sheetColor())) {
        MusicSheetContent(
            state = MusicSheetState(
                title = "Alice — плейлист",
                tracks = tracks,
                current = tracks.first(),
                isPlaying = true,
                positionMs = 4_000,
                durationMs = 844_000,
                speed = 1.5f,
                order = MusicOrder.Reversed,
                repeatMode = MusicRepeatMode.All,
                hasNext = true,
                hasPrevious = false,
                profileButton = ProfileButton.Add,
                profileAction = AsyncAction.Uninitialized,
                canRemoveTracks = true,
                eventSink = {},
            ),
        )
    }
}
