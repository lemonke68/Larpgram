/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.contentvalidation.ContentValidationValue
import io.element.android.libraries.musicplayer.api.MusicFilenames
import io.element.android.libraries.musicplayer.api.MusicPlayer
import io.element.android.libraries.musicplayer.api.MusicSeekBar
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.ui.common.layout.ContentAvoidingLayout
import io.element.android.libraries.ui.common.layout.ContentAvoidingLayoutData
import io.element.android.libraries.ui.utils.time.formatShort
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Общий плеер и комната чата: пузырю нужны оба, чтобы просить теги своего трека. */
@Immutable
class TgChatMusic(
    val player: MusicPlayer,
    val sessionId: SessionId,
    val roomId: RoomId,
)

/**
 * Музыка чата для пузырей аудио. Кладётся в `TgMessagesView` по той же причине, что и
 * [LocalCircleMediaLoader]: пузырь рисуется глубоко в апстримовском таймлайне. null — превью.
 */
val LocalChatMusic = staticCompositionLocalOf<TgChatMusic?> { null }

/** Что пузырь знает о своём треке в общем плеере. */
@Immutable
data class TgAudioPlayback(
    val isCurrent: Boolean,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

private val idlePlayback = TgAudioPlayback(isCurrent = false, isPlaying = false, isBuffering = false, positionMs = 0, durationMs = 0)

/** Все пузыри аудио одной ширины, как в Telegram: играющий не растягивается. */
private val BUBBLE_WIDTH = 250.dp

/**
 * Пузырь аудио как в Telegram: круглая кнопка, название и исполнитель, длительность. Тап по пузырю
 * уже идёт в общий плеер (`MessagesFlowNode.playMusic`), поэтому у кнопки своего обработчика нет.
 * У играющего трека вместо артиста полоса, по которой можно перемотать.
 */
@Composable
fun TgTimelineItemAudioView(
    eventId: EventId?,
    filename: String,
    mimeType: String,
    fileSize: Long?,
    mediaSource: MediaSource,
    duration: Duration?,
    fileExtensionAndSize: String,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit,
    contentValidationValue: ContentValidationValue,
    modifier: Modifier = Modifier,
) {
    val chatMusic = LocalChatMusic.current
    val playback = rememberAudioPlayback(chatMusic?.player, eventId, mediaSource)
    val tags = chatMusic?.player?.tags?.collectAsState()?.value?.get(mediaSource.safeUrl)
    // Нужны ли теги (нет их, старая обложка, настройка автозагрузки), решает плеер.
    LaunchedEffect(chatMusic, mediaSource.safeUrl) {
        if (chatMusic != null) {
            chatMusic.player.requestTags(
                MusicTrack(
                    id = eventId?.value ?: mediaSource.safeUrl,
                    sessionId = chatMusic.sessionId,
                    roomId = chatMusic.roomId,
                    eventId = eventId,
                    mediaSource = mediaSource,
                    filename = filename,
                    mimeType = mimeType,
                    fileSize = fileSize,
                )
            )
        }
    }
    val parsed = remember(filename) { MusicFilenames.parse(filename) }
    TgTimelineItemAudioContent(
        title = tags?.title?.takeIf { it.isNotBlank() } ?: parsed.title,
        artist = tags?.artist?.takeIf { it.isNotBlank() } ?: parsed.artist,
        duration = duration?.takeIf { it.isPositive() },
        fileExtensionAndSize = fileExtensionAndSize,
        playback = playback,
        isLoading = !contentValidationValue.isValidated(),
        onSeek = { positionMs -> chatMusic?.player?.seekTo(positionMs) },
        onContentLayoutChange = onContentLayoutChange,
        modifier = modifier,
    )
}

@Composable
private fun rememberAudioPlayback(player: MusicPlayer?, eventId: EventId?, mediaSource: MediaSource): TgAudioPlayback {
    player ?: return idlePlayback
    val state = player.state.collectAsState()
    // Позиция тикает часто; чужие пузыри перерисовываются, только когда меняется «мой ли трек».
    val playback by remember(player, eventId, mediaSource) {
        derivedStateOf {
            val value = state.value
            val track = value.currentTrack
            val isCurrent = track != null &&
                if (eventId != null) track.eventId == eventId else track.mediaKey == mediaSource.safeUrl
            if (!isCurrent) {
                idlePlayback
            } else {
                TgAudioPlayback(
                    isCurrent = true,
                    isPlaying = value.isPlaying,
                    isBuffering = value.isBuffering,
                    positionMs = value.positionMs,
                    durationMs = value.durationMs,
                )
            }
        }
    }
    return playback
}

@Composable
private fun TgTimelineItemAudioContent(
    title: String,
    artist: String?,
    duration: Duration?,
    fileExtensionAndSize: String,
    playback: TgAudioPlayback,
    isLoading: Boolean,
    onSeek: (Long) -> Unit,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonSize = 44.dp
    val spacing = 10.dp
    // Пока палец на полосе, показываем позицию под пальцем, а не плеера.
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    Row(
        modifier = modifier.width(BUBBLE_WIDTH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(ElementTheme.colors.bgAccentRest),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading || playback.isBuffering) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Icon(
                    imageVector = if (playback.isPlaying) CompoundIcons.PauseSolid() else CompoundIcons.PlaySolid(),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (playback.isCurrent) ElementTheme.colors.textActionAccent else ElementTheme.colors.textPrimary,
                style = ElementTheme.typography.fontBodyLgMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (playback.isCurrent && playback.durationMs > 0) {
                MusicSeekBar(
                    progress = seekFraction ?: playback.progress,
                    activeColor = ElementTheme.colors.textActionAccent,
                    inactiveColor = ElementTheme.colors.textSecondary.copy(alpha = 0.3f),
                    onSeekChange = { seekFraction = it },
                    onSeekFinish = { fraction ->
                        seekFraction = null
                        onSeek((fraction * playback.durationMs).toLong())
                    },
                )
            } else {
                Text(
                    text = artist.orEmpty(),
                    color = ElementTheme.colors.textSecondary,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val shownPositionMs = seekFraction?.let { (it * playback.durationMs).toLong() } ?: playback.positionMs
            val time = when {
                playback.isCurrent && duration != null -> "${shownPositionMs.milliseconds.formatShort()} / ${duration.formatShort()}"
                playback.isCurrent && playback.durationMs > 0 ->
                    "${shownPositionMs.milliseconds.formatShort()} / ${playback.durationMs.milliseconds.formatShort()}"
                duration != null -> "${duration.formatShort()} · $fileExtensionAndSize"
                else -> fileExtensionAndSize
            }
            Text(
                text = time,
                color = ElementTheme.colors.textSecondary,
                style = ElementTheme.typography.fontBodySmRegular,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = ContentAvoidingLayout.measureLastTextLine(
                    onContentLayoutChange = onContentLayoutChange,
                    extraWidth = buttonSize + spacing,
                ),
            )
        }
    }
}

internal data class TgAudioPreview(
    val title: String,
    val artist: String?,
    val duration: Duration?,
    val playback: TgAudioPlayback,
)

internal class TgAudioPreviewProvider : PreviewParameterProvider<TgAudioPreview> {
    override val values = sequenceOf(
        TgAudioPreview("Veridis Quo", "Daft Punk", 345_000.milliseconds, idlePlayback),
        TgAudioPreview("demo_master_v3_final", null, null, idlePlayback),
        TgAudioPreview(
            "Very long track title that does not fit on one line at all",
            "Artist",
            200_000.milliseconds,
            TgAudioPlayback(isCurrent = true, isPlaying = true, isBuffering = false, positionMs = 80_000, durationMs = 200_000),
        ),
        TgAudioPreview(
            "Paused",
            "Artist",
            200_000.milliseconds,
            TgAudioPlayback(isCurrent = true, isPlaying = false, isBuffering = false, positionMs = 140_000, durationMs = 200_000),
        ),
    )
}

@PreviewsDayNight
@Composable
internal fun TgTimelineItemAudioViewPreview(@PreviewParameter(TgAudioPreviewProvider::class) preview: TgAudioPreview) =
    ElementTimelineItemPreview {
        TgTimelineItemAudioContent(
            title = preview.title,
            artist = preview.artist,
            duration = preview.duration,
            fileExtensionAndSize = "FLAC, 32.4 MB",
            playback = preview.playback,
            isLoading = false,
            onSeek = {},
            onContentLayoutChange = {},
        )
    }
