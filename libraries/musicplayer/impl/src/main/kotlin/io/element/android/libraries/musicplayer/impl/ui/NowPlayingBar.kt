/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.glass.TgGlassDefaults
import io.element.android.libraries.designsystem.components.glass.tgGlass
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.musicplayer.impl.R

/** Что показывает пилюля «сейчас играет». */
internal data class NowPlayingBarState(
    val title: String,
    val artist: String?,
    val isPlaying: Boolean,
    val progress: Float,
    val speed: Float,
)

internal sealed interface NowPlayingBarAction {
    data object PlayPause : NowPlayingBarAction
    data object Open : NowPlayingBarAction
    data object CycleSpeed : NowPlayingBarAction
    data object Close : NowPlayingBarAction
}

/**
 * Правка форка: полоса «сейчас играет» Telegram (`FragmentContextView`) в виде стеклянной пилюли
 * под шапкой, как закреплённое сообщение: play/pause слева, название и артист по центру, скорость
 * и закрыть справа, тонкая полоска прогресса по низу. Тап по середине — шторка плеера.
 */
@Composable
internal fun NowPlayingBar(
    state: NowPlayingBarState,
    onAction: (NowPlayingBarAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(TgGlassDefaults.inputRadius)
    val accent = ElementTheme.colors.bgAccentRest
    val track = ElementTheme.colors.borderDisabled
    Row(
        modifier = modifier
            .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues())
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .tgGlass(shape)
            .drawWithContent {
                drawContent()
                // Прогресс — тонкая линия по низу пилюли, концы не заходят на скругления.
                val inset = 18.dp.toPx()
                val height = 2.dp.toPx()
                val y = size.height - height - 1.dp.toPx()
                val width = size.width - inset * 2
                val radius = CornerRadius(height / 2)
                drawRoundRect(color = track, topLeft = Offset(inset, y), size = Size(width, height), cornerRadius = radius)
                drawRoundRect(color = accent, topLeft = Offset(inset, y), size = Size(width * state.progress, height), cornerRadius = radius)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onAction(NowPlayingBarAction.PlayPause) }) {
            Icon(
                imageVector = if (state.isPlaying) CompoundIcons.PauseSolid() else CompoundIcons.PlaySolid(),
                contentDescription = stringResource(if (state.isPlaying) R.string.larpgram_music_pause else R.string.larpgram_music_play),
                tint = ElementTheme.colors.iconPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        val density = LocalDensity.current
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClickLabel = stringResource(R.string.larpgram_music_open_player)) { onAction(NowPlayingBarAction.Open) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = state.title,
                style = ElementTheme.typography.fontBodyMdMedium.copy(
                    fontSize = with(density) { 14.dp.toSp() },
                    lineHeight = with(density) { 17.dp.toSp() },
                    fontWeight = FontWeight.Medium,
                ),
                color = ElementTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.artist != null) {
                Text(
                    text = state.artist,
                    style = ElementTheme.typography.fontBodySmRegular.copy(
                        fontSize = with(density) { 12.dp.toSp() },
                        lineHeight = with(density) { 14.dp.toSp() },
                    ),
                    color = ElementTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SpeedButton(speed = state.speed, onClick = { onAction(NowPlayingBarAction.CycleSpeed) })
        IconButton(onClick = { onAction(NowPlayingBarAction.Close) }) {
            Icon(
                imageVector = CompoundIcons.Close(),
                contentDescription = stringResource(R.string.larpgram_music_close),
                tint = TgGlassDefaults.iconColor(),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Высота пилюли — как у кнопок шапки (`TgChatHeader`). */
private val BAR_HEIGHT = 44.dp

@PreviewsDayNight
@Composable
internal fun NowPlayingBarPreview() = ElementPreview {
    Column {
        NowPlayingBar(
            state = NowPlayingBarState(title = "подожди мне звонят2.mp3", artist = "Unknown Artist", isPlaying = true, progress = 0.3f, speed = 1f),
            onAction = {},
        )
        NowPlayingBar(
            state = NowPlayingBarState(title = "Veridis Quo", artist = null, isPlaying = false, progress = 0.8f, speed = 1.5f),
            onAction = {},
        )
    }
}
