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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.ChatAppearanceDefaults
import io.element.android.libraries.designsystem.theme.ChatThemeOption
import io.element.android.libraries.designsystem.theme.ChatWallpaperOption
import io.element.android.libraries.designsystem.theme.chatWallpaperBackground
import io.element.android.libraries.designsystem.theme.contentColorForBubble
import io.element.android.libraries.designsystem.theme.components.Icon
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
    val isCustomSelected = state.chatWallpaperId == ChatWallpaperOption.CUSTOM_ID
    val customColor = state.chatWallpaperCustomColorArgb?.let { Color(it) }
    val selectedWallpaper = ChatWallpaperOption.fromId(state.chatWallpaperId)
    val previewColor = if (isCustomSelected && customColor != null) {
        customColor
    } else {
        wallpaperSwatchColor(selectedWallpaper)
    }
    val bubbleColor = state.chatBubbleColorArgb?.let { Color(it) }
    val accentColor = state.chatAccentColorArgb?.let { Color(it) }
    val selectedTheme = ChatThemeOption.matching(
        state.chatWallpaperId,
        state.chatBubbleColorArgb,
        state.chatAccentColorArgb,
    )
    var showColorPicker by remember { mutableStateOf(false) }
    var showBubbleColorPicker by remember { mutableStateOf(false) }
    var showAccentColorPicker by remember { mutableStateOf(false) }

    ChatAppearancePreview(
        messageTextSizeSp = state.messageTextSizeSp,
        bubbleCornerRadiusDp = state.bubbleCornerRadiusDp,
        wallpaperColor = previewColor,
        bubbleColor = bubbleColor,
    )

    // Готовые темы = связка обои+пузырь одним тапом (палитра согласована).
    ThemePresetRow(
        selected = selectedTheme,
        onSelect = { state.eventSink(AdvancedSettingsEvents.ApplyChatTheme(it.id)) },
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
        isCustomSelected = isCustomSelected,
        customColor = customColor,
        onSelect = { state.eventSink(AdvancedSettingsEvents.SetChatWallpaper(it.id)) },
        onEyedropperClick = { showColorPicker = true },
    )

    BubbleColorRow(
        bubbleColor = bubbleColor,
        onSelectDefault = { state.eventSink(AdvancedSettingsEvents.SetChatBubbleColor(null)) },
        onSelectColor = { state.eventSink(AdvancedSettingsEvents.SetChatBubbleColor(it.toArgb())) },
        onEyedropperClick = { showBubbleColorPicker = true },
    )

    AccentColorRow(
        accentColor = accentColor,
        onSelectDefault = { state.eventSink(AdvancedSettingsEvents.SetChatAccentColor(null)) },
        onSelectColor = { state.eventSink(AdvancedSettingsEvents.SetChatAccentColor(it.toArgb())) },
        onEyedropperClick = { showAccentColorPicker = true },
    )

    if (showColorPicker) {
        ChatWallpaperColorPickerDialog(
            initialColor = customColor ?: previewColor,
            onColorSelected = {
                state.eventSink(AdvancedSettingsEvents.SetChatWallpaperCustomColor(it.toArgb()))
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }

    if (showBubbleColorPicker) {
        ChatWallpaperColorPickerDialog(
            initialColor = bubbleColor ?: ElementTheme.colors.messageFromMeBackground,
            onColorSelected = {
                state.eventSink(AdvancedSettingsEvents.SetChatBubbleColor(it.toArgb()))
                showBubbleColorPicker = false
            },
            onDismiss = { showBubbleColorPicker = false },
        )
    }

    if (showAccentColorPicker) {
        ChatWallpaperColorPickerDialog(
            initialColor = accentColor ?: ElementTheme.colors.iconAccentTertiary,
            onColorSelected = {
                state.eventSink(AdvancedSettingsEvents.SetChatAccentColor(it.toArgb()))
                showAccentColorPicker = false
            },
            onDismiss = { showAccentColorPicker = false },
        )
    }
}

/** Quick accent colors offered as swatches next to the default + eyedropper. */
private val ACCENT_QUICK_COLORS = listOf(
    Color(0xFF3D82D6),
    Color(0xFF3B8F6B),
    Color(0xFFC4703C),
    Color(0xFFB5474E),
    Color(0xFF2FA8A8),
)

/** Quick outgoing-bubble colors offered as swatches next to the default + eyedropper. */
private val BUBBLE_QUICK_COLORS = listOf(
    Color(0xFF2E5C99),
    Color(0xFF3B8F6B),
    Color(0xFF6949A8),
    Color(0xFFC4703C),
    Color(0xFFB5474E),
)

@Composable
private fun wallpaperSwatchColor(option: ChatWallpaperOption): Color =
    option.solidColor ?: ElementTheme.colors.chatWallpaperBackground

@Composable
private fun ChatAppearancePreview(
    messageTextSizeSp: Int,
    bubbleCornerRadiusDp: Int,
    wallpaperColor: Color,
    bubbleColor: Color?,
) {
    val bubbleShape = RoundedCornerShape(bubbleCornerRadiusDp.dp)
    val outgoingBg = bubbleColor ?: ElementTheme.colors.messageFromMeBackground
    val outgoingText = if (bubbleColor != null) contentColorForBubble(bubbleColor) else ElementTheme.colors.textPrimary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(wallpaperColor)
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
                backgroundColor = outgoingBg,
                textColor = outgoingText,
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
    isCustomSelected: Boolean,
    customColor: Color?,
    onSelect: (ChatWallpaperOption) -> Unit,
    onEyedropperClick: () -> Unit,
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
        // Пипетка: любой цвет через RGB-пикер. Слева от пресетов.
        EyedropperSwatch(
            customColor = customColor,
            isSelected = isCustomSelected,
            onClick = onEyedropperClick,
        )
        ChatWallpaperOption.entries.forEach { option ->
            WallpaperSwatch(
                option = option,
                isSelected = !isCustomSelected && option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun EyedropperSwatch(
    customColor: Color?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val ringColor = if (isSelected) ElementTheme.colors.iconAccentTertiary else ElementTheme.colors.borderInteractiveSecondary
    // Радужная заливка = «выбрать любой цвет»; если цвет уже выбран, показываем его.
    val rainbow = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFE0554E),
                Color(0xFFE0A64E),
                Color(0xFFD8D24E),
                Color(0xFF5B9E6F),
                Color(0xFF5A86D8),
                Color(0xFF8A5AD8),
                Color(0xFFE0554E),
            )
        )
    }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (customColor != null) Modifier.background(customColor) else Modifier.background(rainbow)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = ringColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CompoundIcons.Edit(),
            contentDescription = stringResource(R.string.screen_chat_appearance_custom_color_title),
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
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

@Composable
private fun ColumnScope.ThemePresetRow(
    selected: ChatThemeOption?,
    onSelect: (ChatThemeOption) -> Unit,
) {
    Text(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        text = stringResource(R.string.screen_chat_appearance_theme_title),
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
        ChatThemeOption.entries.forEach { theme ->
            ThemeSwatch(
                theme = theme,
                isSelected = theme == selected,
                onClick = { onSelect(theme) },
            )
        }
    }
}

/** Mini theme preview: the wallpaper color with a single outgoing bubble in the theme's color. */
@Composable
private fun ThemeSwatch(
    theme: ChatThemeOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val ringColor = if (isSelected) ElementTheme.colors.iconAccentTertiary else ElementTheme.colors.borderInteractiveSecondary
    val bubbleColor = theme.bubbleColor ?: ElementTheme.colors.messageFromMeBackground
    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(wallpaperSwatchColor(theme.wallpaper))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = ringColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .size(width = 30.dp, height = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bubbleColor),
        )
    }
}

@Composable
private fun ColumnScope.BubbleColorRow(
    bubbleColor: Color?,
    onSelectDefault: () -> Unit,
    onSelectColor: (Color) -> Unit,
    onEyedropperClick: () -> Unit,
) {
    Text(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        text = stringResource(R.string.screen_chat_appearance_bubble_color_title),
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
        // Дефолт = вернуть тема-зависимый цвет пузыря.
        BubbleColorSwatch(
            color = ElementTheme.colors.messageFromMeBackground,
            isSelected = bubbleColor == null,
            onClick = onSelectDefault,
        )
        // Пипетка: любой цвет пузыря.
        EyedropperSwatch(
            customColor = bubbleColor.takeIf { c -> c != null && BUBBLE_QUICK_COLORS.none { it == c } },
            isSelected = bubbleColor != null && BUBBLE_QUICK_COLORS.none { it == bubbleColor },
            onClick = onEyedropperClick,
        )
        BUBBLE_QUICK_COLORS.forEach { color ->
            BubbleColorSwatch(
                color = color,
                isSelected = bubbleColor == color,
                onClick = { onSelectColor(color) },
            )
        }
    }
}

@Composable
private fun ColumnScope.AccentColorRow(
    accentColor: Color?,
    onSelectDefault: () -> Unit,
    onSelectColor: (Color) -> Unit,
    onEyedropperClick: () -> Unit,
) {
    Text(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        text = stringResource(R.string.screen_chat_appearance_accent_color_title),
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
        // Дефолт = вернуть брендовый акцент.
        BubbleColorSwatch(
            color = ElementTheme.colors.iconAccentTertiary,
            isSelected = accentColor == null,
            onClick = onSelectDefault,
        )
        EyedropperSwatch(
            customColor = accentColor.takeIf { c -> c != null && ACCENT_QUICK_COLORS.none { it == c } },
            isSelected = accentColor != null && ACCENT_QUICK_COLORS.none { it == accentColor },
            onClick = onEyedropperClick,
        )
        ACCENT_QUICK_COLORS.forEach { color ->
            BubbleColorSwatch(
                color = color,
                isSelected = accentColor == color,
                onClick = { onSelectColor(color) },
            )
        }
    }
}

@Composable
private fun BubbleColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val ringColor = if (isSelected) ElementTheme.colors.iconAccentTertiary else ElementTheme.colors.borderInteractiveSecondary
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
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
