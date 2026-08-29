/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.ChatAppearanceDefaults
import io.element.android.libraries.designsystem.theme.ChatWallpaperOption
import io.element.android.libraries.designsystem.theme.chatWallpaperBackground
import io.element.android.libraries.designsystem.theme.components.Slider
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.messageFromMeBackground
import io.element.android.libraries.designsystem.theme.messageFromOtherBackground
import kotlin.math.roundToInt

/**
 * Larpgram: «Настройки чатов» — размер текста сообщений и радиус углов пузырей. Два ползунка живьём
 * гонят значения в `AppPreferencesStore`, поэтому и превью сверху, и реальный таймлайн приложения
 * (через CompositionLocal-ы в `ElementThemeApp`) обновляются одновременно.
 *
 * Превью — упрощённое: обычный `RoundedCornerShape` без хвостика, только чтобы судить размер и
 * скругление. Хвост и точная геометрия — на живом экране, юзер доводит на устройстве.
 */
@Composable
fun ColumnScope.ChatAppearanceSection(state: AdvancedSettingsState) {
    val selectedWallpaper = ChatWallpaperOption.fromId(state.chatWallpaperId)
    ChatAppearancePreview(
        messageTextSizeSp = state.messageTextSizeSp,
        bubbleCornerRadiusDp = state.bubbleCornerRadiusDp,
        wallpaper = selectedWallpaper,
    )

    SliderRow(
        title = stringResource(R.string.screen_chat_appearance_text_size_title),
        value = state.messageTextSizeSp,
        valueRange = ChatAppearanceDefaults.TEXT_SIZE_MIN_SP..ChatAppearanceDefaults.TEXT_SIZE_MAX_SP,
        onValueChange = { state.eventSink(AdvancedSettingsEvents.SetMessageTextSize(it)) },
    )

    SliderRow(
        title = stringResource(R.string.screen_chat_appearance_corner_radius_title),
        value = state.bubbleCornerRadiusDp,
        valueRange = ChatAppearanceDefaults.BUBBLE_RADIUS_MIN_DP..ChatAppearanceDefaults.BUBBLE_RADIUS_MAX_DP,
        onValueChange = { state.eventSink(AdvancedSettingsEvents.SetBubbleCornerRadius(it)) },
    )

    WallpaperRow(
        selected = selectedWallpaper,
        onSelect = { state.eventSink(AdvancedSettingsEvents.SetChatWallpaper(it.id)) },
    )
}

@Composable
private fun wallpaperSwatchColor(option: ChatWallpaperOption): androidx.compose.ui.graphics.Color =
    option.solidColor ?: ElementTheme.colors.chatWallpaperBackground

@Composable
private fun ChatAppearancePreview(
    messageTextSizeSp: Int,
    bubbleCornerRadiusDp: Int,
    wallpaper: ChatWallpaperOption,
) {
    val bubbleShape = RoundedCornerShape(bubbleCornerRadiusDp.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(wallpaperSwatchColor(wallpaper))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewBubble(
                text = stringResource(R.string.screen_chat_appearance_preview_incoming),
                backgroundColor = ElementTheme.colors.messageFromOtherBackground,
                textColor = ElementTheme.colors.textPrimary,
                textSizeSp = messageTextSizeSp,
                shape = bubbleShape,
                alignment = Alignment.Start,
            )
            PreviewBubble(
                text = stringResource(R.string.screen_chat_appearance_preview_outgoing),
                backgroundColor = ElementTheme.colors.messageFromMeBackground,
                textColor = ElementTheme.colors.textPrimary,
                textSizeSp = messageTextSizeSp,
                shape = bubbleShape,
                alignment = Alignment.End,
            )
        }
    }
}

@Composable
private fun PreviewBubble(
    text: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    textSizeSp: Int,
    shape: androidx.compose.ui.graphics.Shape,
    alignment: Alignment.Horizontal,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = text,
                color = textColor,
                style = ElementTheme.typography.fontBodyLgRegular.copy(fontSize = textSizeSp.sp),
            )
        }
    }
}

@Composable
private fun ColumnScope.SliderRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = value.toString(),
                textAlign = TextAlign.End,
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textActionAccent,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ColumnScope.WallpaperRow(
    selected: ChatWallpaperOption,
    onSelect: (ChatWallpaperOption) -> Unit,
) {
    Text(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        text = stringResource(R.string.screen_chat_appearance_wallpaper_title),
        style = ElementTheme.typography.fontBodyLgRegular,
        color = ElementTheme.colors.textPrimary,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChatWallpaperOption.entries.forEach { option ->
            WallpaperSwatch(
                option = option,
                isSelected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun WallpaperSwatch(
    option: ChatWallpaperOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val ringColor = if (isSelected) ElementTheme.colors.iconAccentTertiary else ElementTheme.colors.borderInteractiveSecondary
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(wallpaperSwatchColor(option))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = ringColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
    )
}

@PreviewsDayNight
@Composable
internal fun ChatAppearanceSectionPreview() = ElementPreview {
    Column {
        ChatAppearanceSection(
            state = aAdvancedSettingsState(
                messageTextSizeSp = 18,
                bubbleCornerRadiusDp = 12,
                chatWallpaperId = ChatWallpaperOption.Navy.id,
            ),
        )
    }
}
