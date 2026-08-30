/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.root.TgSettingsColors
import io.element.android.features.preferences.impl.root.TgSettingsGroup
import io.element.android.features.preferences.impl.root.TgSettingsItem
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
 * Larpgram: «Настройки чатов» разбито на два экрана в духе Telegram.
 *
 * [ChatAppearanceSection] — главный экран (размер текста, углы блоков, обои, переход в редактор темы).
 * [ChatThemeSection] — экран «Настройки темы» (пресеты + выбор объекта настройки через выпадающее
 * меню: обои / цвет акцентов / мои сообщения).
 *
 * Все ползунки и свотчи живьём гонят значения в `AppPreferencesStore`, поэтому и превью, и реальный
 * таймлайн (через CompositionLocal-ы в `ElementThemeApp`) обновляются одновременно. Каждый набор
 * настроек лежит в своей карточке [TgSettingsGroup], как секции в TG.
 *
 * Превью — упрощённое (без хвостика пузыря): только чтобы судить размер, скругление и цвет.
 */

/** Диаметр круглых цветовых свотчей (обои/пузырь/акцент). Мельче прежних 52dp квадратов. */
private val SWATCH_SIZE = 38.dp

/**
 * Пикер картинки для обоев: системный SAF (OpenDocument), берём стойкое разрешение на чтение URI,
 * чтобы обои пережили перезапуск. Возвращает лямбду-запуск. onPicked получает URI строкой.
 */
@Composable
private fun rememberWallpaperImagePicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onPicked(uri.toString())
        }
    }
    return { launcher.launch(arrayOf("image/*")) }
}

/** Объект настройки, выбираемый в выпадающем меню на экране «Настройки темы». */
private enum class ThemeEditTarget(val titleRes: Int) {
    Wallpaper(R.string.screen_chat_appearance_wallpaper_title),
    Accent(R.string.screen_chat_appearance_accent_color_title),
    Bubble(R.string.screen_chat_appearance_bubble_color_title),
}

// ---- Главный экран «Настройки чатов» --------------------------------------------------------

@Composable
fun ColumnScope.ChatAppearanceSection(
    state: AdvancedSettingsState,
    onOpenChatThemeSettings: () -> Unit,
) {
    val isCustomSelected = state.chatWallpaperId == ChatWallpaperOption.CUSTOM_ID
    val customColor = state.chatWallpaperCustomColorArgb?.let { Color(it) }
    val selectedWallpaper = ChatWallpaperOption.fromId(state.chatWallpaperId)
    val isImageSelected = state.chatWallpaperId == ChatWallpaperOption.CUSTOM_IMAGE_ID
    val imageUri = state.chatWallpaperImageUri
    val previewColor = if (isCustomSelected && customColor != null) customColor else wallpaperSwatchColor(selectedWallpaper)
    val bubbleColor = state.chatBubbleColorArgb?.let { Color(it) }
    var showColorPicker by remember { mutableStateOf(false) }
    val pickImage = rememberWallpaperImagePicker { state.eventSink(AdvancedSettingsEvents.SetChatWallpaperImage(it)) }

    TgSettingsGroup {
        SliderRow(
            title = stringResource(R.string.screen_chat_appearance_text_size_title),
            value = state.messageTextSizeSp,
            valueRange = ChatAppearanceDefaults.TEXT_SIZE_MIN_SP..ChatAppearanceDefaults.TEXT_SIZE_MAX_SP,
            onValueChange = { state.eventSink(AdvancedSettingsEvents.SetMessageTextSize(it)) },
        )
    }

    ChatAppearancePreview(
        messageTextSizeSp = state.messageTextSizeSp,
        bubbleCornerRadiusDp = state.bubbleCornerRadiusDp,
        wallpaperColor = previewColor,
        wallpaperImageUri = imageUri.takeIf { isImageSelected },
        bubbleColor = bubbleColor,
    )

    TgSettingsGroup {
        SliderRow(
            title = stringResource(R.string.screen_chat_appearance_corner_radius_title),
            value = state.bubbleCornerRadiusDp,
            valueRange = ChatAppearanceDefaults.BUBBLE_RADIUS_MIN_DP..ChatAppearanceDefaults.BUBBLE_RADIUS_MAX_DP,
            onValueChange = { state.eventSink(AdvancedSettingsEvents.SetBubbleCornerRadius(it)) },
        )
    }

    TgSettingsGroup {
        WallpaperRow(
            selected = selectedWallpaper,
            isCustomSelected = isCustomSelected,
            isImageSelected = isImageSelected,
            imageUri = imageUri,
            customColor = customColor,
            onSelect = { state.eventSink(AdvancedSettingsEvents.SetChatWallpaper(it.id)) },
            onEyedropperClick = { showColorPicker = true },
            onPickImage = pickImage,
        )
    }

    TgSettingsGroup {
        TgSettingsItem(
            title = stringResource(R.string.screen_chat_theme_settings_title),
            color = TgSettingsColors.Purple,
            iconVector = CompoundIcons.Edit(),
            onClick = onOpenChatThemeSettings,
        )
    }

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
}

// ---- Экран «Настройки темы» -----------------------------------------------------------------

@Composable
fun ColumnScope.ChatThemeSection(state: AdvancedSettingsState) {
    val isCustomSelected = state.chatWallpaperId == ChatWallpaperOption.CUSTOM_ID
    val customColor = state.chatWallpaperCustomColorArgb?.let { Color(it) }
    val selectedWallpaper = ChatWallpaperOption.fromId(state.chatWallpaperId)
    val isImageSelected = state.chatWallpaperId == ChatWallpaperOption.CUSTOM_IMAGE_ID
    val imageUri = state.chatWallpaperImageUri
    val previewColor = if (isCustomSelected && customColor != null) customColor else wallpaperSwatchColor(selectedWallpaper)
    val bubbleColor = state.chatBubbleColorArgb?.let { Color(it) }
    val accentColor = state.chatAccentColorArgb?.let { Color(it) }
    val selectedTheme = ChatThemeOption.matching(
        state.chatWallpaperId,
        state.chatBubbleColorArgb,
        state.chatAccentColorArgb,
    )
    var editTarget by remember { mutableStateOf(ThemeEditTarget.Wallpaper) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showBubbleColorPicker by remember { mutableStateOf(false) }
    var showAccentColorPicker by remember { mutableStateOf(false) }
    val pickImage = rememberWallpaperImagePicker { state.eventSink(AdvancedSettingsEvents.SetChatWallpaperImage(it)) }

    ChatAppearancePreview(
        messageTextSizeSp = state.messageTextSizeSp,
        bubbleCornerRadiusDp = state.bubbleCornerRadiusDp,
        wallpaperColor = previewColor,
        wallpaperImageUri = imageUri.takeIf { isImageSelected },
        bubbleColor = bubbleColor,
    )

    // Готовые темы = связка обои+пузырь+акцент одним тапом (палитра согласована).
    TgSettingsGroup {
        SectionLabel(stringResource(R.string.screen_chat_theme_select_title))
        ThemePresetRow(
            selected = selectedTheme,
            onSelect = { state.eventSink(AdvancedSettingsEvents.ApplyChatTheme(it.id)) },
        )
    }

    // Тонкая настройка: выпадающее меню выбирает объект, под ним — его свотчи.
    TgSettingsGroup {
        ThemeTargetDropdown(selected = editTarget, onSelect = { editTarget = it })
        when (editTarget) {
            ThemeEditTarget.Wallpaper -> WallpaperRow(
                selected = selectedWallpaper,
                isCustomSelected = isCustomSelected,
                isImageSelected = isImageSelected,
                imageUri = imageUri,
                customColor = customColor,
                onSelect = { state.eventSink(AdvancedSettingsEvents.SetChatWallpaper(it.id)) },
                onEyedropperClick = { showColorPicker = true },
                onPickImage = pickImage,
            )
            ThemeEditTarget.Accent -> AccentColorRow(
                accentColor = accentColor,
                onSelectDefault = { state.eventSink(AdvancedSettingsEvents.SetChatAccentColor(null)) },
                onSelectColor = { state.eventSink(AdvancedSettingsEvents.SetChatAccentColor(it.toArgb())) },
                onEyedropperClick = { showAccentColorPicker = true },
            )
            ThemeEditTarget.Bubble -> BubbleColorRow(
                bubbleColor = bubbleColor,
                onSelectDefault = { state.eventSink(AdvancedSettingsEvents.SetChatBubbleColor(null)) },
                onSelectColor = { state.eventSink(AdvancedSettingsEvents.SetChatBubbleColor(it.toArgb())) },
                onEyedropperClick = { showBubbleColorPicker = true },
            )
        }
    }

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
private fun ColumnScope.SectionLabel(text: String) {
    Text(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
        text = text,
        style = ElementTheme.typography.fontBodyMdMedium,
        color = ElementTheme.colors.textSecondary,
    )
}

@Composable
private fun ChatAppearancePreview(
    messageTextSizeSp: Int,
    bubbleCornerRadiusDp: Int,
    wallpaperColor: Color,
    wallpaperImageUri: String?,
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
            .background(wallpaperColor),
    ) {
        if (wallpaperImageUri != null) {
            AsyncImage(
                model = wallpaperImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
    backgroundColor: Color,
    textColor: Color,
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

/** Выпадающее меню выбора объекта настройки темы (обои / акцент / мои сообщения). */
@Composable
private fun ColumnScope.ThemeTargetDropdown(
    selected: ThemeEditTarget,
    onSelect: (ThemeEditTarget) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.screen_chat_theme_customize_title),
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                text = stringResource(selected.titleRes),
                textAlign = TextAlign.End,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textActionAccent,
            )
            Icon(
                imageVector = CompoundIcons.ChevronDown(),
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeEditTarget.entries.forEach { target ->
                DropdownMenuItem(
                    text = { Text(stringResource(target.titleRes)) },
                    onClick = {
                        onSelect(target)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.WallpaperRow(
    selected: ChatWallpaperOption,
    isCustomSelected: Boolean,
    isImageSelected: Boolean,
    imageUri: String?,
    customColor: Color?,
    onSelect: (ChatWallpaperOption) -> Unit,
    onEyedropperClick: () -> Unit,
    onPickImage: () -> Unit,
) {
    SwatchRow {
        // Своя фотография: слева, открывает системный пикер картинки.
        PhotoSwatch(
            imageUri = imageUri,
            isSelected = isImageSelected,
            onClick = onPickImage,
        )
        // Пипетка: любой цвет через RGB-пикер.
        EyedropperSwatch(
            customColor = customColor,
            isSelected = isCustomSelected,
            onClick = onEyedropperClick,
        )
        ChatWallpaperOption.entries.forEach { option ->
            ColorSwatch(
                color = wallpaperSwatchColor(option),
                isSelected = !isCustomSelected && !isImageSelected && option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

/** Круглый свотч «своя фотография»: превью выбранной картинки либо иконка-плейсхолдер. */
@Composable
private fun PhotoSwatch(
    imageUri: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val ringColor = if (isSelected) ElementTheme.colors.iconAccentTertiary else ElementTheme.colors.borderInteractiveSecondary
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(ElementTheme.colors.bgSubtlePrimary)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = ringColor,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = stringResource(R.string.screen_chat_appearance_wallpaper_title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = CompoundIcons.Image(),
                contentDescription = stringResource(R.string.screen_chat_appearance_wallpaper_title),
                tint = ElementTheme.colors.iconSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.BubbleColorRow(
    bubbleColor: Color?,
    onSelectDefault: () -> Unit,
    onSelectColor: (Color) -> Unit,
    onEyedropperClick: () -> Unit,
) {
    SwatchRow {
        // Дефолт = вернуть тема-зависимый цвет пузыря.
        ColorSwatch(
            color = ElementTheme.colors.messageFromMeBackground,
            isSelected = bubbleColor == null,
            onClick = onSelectDefault,
        )
        EyedropperSwatch(
            customColor = bubbleColor.takeIf { c -> c != null && BUBBLE_QUICK_COLORS.none { it == c } },
            isSelected = bubbleColor != null && BUBBLE_QUICK_COLORS.none { it == bubbleColor },
            onClick = onEyedropperClick,
        )
        BUBBLE_QUICK_COLORS.forEach { color ->
            ColorSwatch(
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
    SwatchRow {
        // Дефолт = вернуть брендовый акцент.
        ColorSwatch(
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
            ColorSwatch(
                color = color,
                isSelected = accentColor == color,
                onClick = { onSelectColor(color) },
            )
        }
    }
}

/** Horizontally scrollable row of round color swatches, shared by wallpaper/bubble/accent. */
@Composable
private fun SwatchRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
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
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .then(if (customColor != null) Modifier.background(customColor) else Modifier.background(rainbow))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = ringColor,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CompoundIcons.Edit(),
            contentDescription = stringResource(R.string.screen_chat_appearance_custom_color_title),
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val ringColor = if (isSelected) ElementTheme.colors.iconAccentTertiary else ElementTheme.colors.borderInteractiveSecondary
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = ringColor,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ColumnScope.ThemePresetRow(
    selected: ChatThemeOption?,
    onSelect: (ChatThemeOption) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

/** Mini theme preview card: the wallpaper color with a single outgoing bubble in the theme's color. */
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
            onOpenChatThemeSettings = {},
        )
    }
}

@PreviewsDayNight
@Composable
internal fun ChatThemeSectionPreview() = ElementPreview {
    Column {
        ChatThemeSection(
            state = aAdvancedSettingsState(
                messageTextSizeSp = 18,
                bubbleCornerRadiusDp = 12,
                chatWallpaperId = ChatWallpaperOption.Navy.id,
            ),
        )
    }
}
