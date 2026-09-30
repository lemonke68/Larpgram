/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.withLarpgramAccent
import io.element.android.libraries.preferences.api.store.ChatAppearanceStore
import io.element.android.libraries.preferences.api.store.DEFAULT_BUBBLE_CORNER_RADIUS_DP
import io.element.android.libraries.preferences.api.store.DEFAULT_MESSAGE_TEXT_SIZE_SP

/**
 * Provides the chat appearance settings app-wide for the timeline to read, and hands [content] the palettes
 * with the chosen accent applied. Called from [ElementThemeApp] around Element's theme.
 */
@Composable
fun ProvideChatAppearance(
    chatAppearanceStore: ChatAppearanceStore,
    compoundLight: SemanticColors,
    compoundDark: SemanticColors,
    content: @Composable (compoundLight: SemanticColors, compoundDark: SemanticColors) -> Unit,
) {
    val messageTextSizeSp by remember {
        chatAppearanceStore.getMessageTextSizeSpFlow()
    }.collectAsState(initial = DEFAULT_MESSAGE_TEXT_SIZE_SP)
    val bubbleCornerRadiusDp by remember {
        chatAppearanceStore.getBubbleCornerRadiusDpFlow()
    }.collectAsState(initial = DEFAULT_BUBBLE_CORNER_RADIUS_DP)
    val chatWallpaperId by remember {
        chatAppearanceStore.getChatWallpaperIdFlow()
    }.collectAsState(initial = null)
    val chatWallpaperCustomColorArgb by remember {
        chatAppearanceStore.getChatWallpaperCustomColorArgbFlow()
    }.collectAsState(initial = null)
    val chatBubbleColorArgb by remember {
        chatAppearanceStore.getChatBubbleColorArgbFlow()
    }.collectAsState(initial = null)
    val chatAccentColorArgb by remember {
        chatAppearanceStore.getChatAccentColorArgbFlow()
    }.collectAsState(initial = null)
    val chatWallpaperImageUri by remember {
        chatAppearanceStore.getChatWallpaperImageUriFlow()
    }.collectAsState(initial = null)
    val chatListThreeLine by remember {
        chatAppearanceStore.getChatListThreeLineFlow()
    }.collectAsState(initial = false)
    val chatWallpaperGradientSpec by remember {
        chatAppearanceStore.getChatWallpaperGradientFlow()
    }.collectAsState(initial = null)
    val chatWallpaperGradient = remember(chatWallpaperGradientSpec) {
        ChatWallpaperGradient.parse(chatWallpaperGradientSpec)
    }
    // A chosen accent rebuilds the whole accent family on top of the themed palettes.
    val accentColor = chatAccentColorArgb?.let { Color(it) }
    val effectiveCompoundLight = remember(compoundLight, accentColor) {
        accentColor?.let { compoundLight.withLarpgramAccent(it, isLight = true) } ?: compoundLight
    }
    val effectiveCompoundDark = remember(compoundDark, accentColor) {
        accentColor?.let { compoundDark.withLarpgramAccent(it, isLight = false) } ?: compoundDark
    }
    CompositionLocalProvider(
        LocalMessageTextScale provides ChatAppearanceDefaults.textScaleFor(messageTextSizeSp),
        LocalChatBubbleRadius provides ChatAppearanceDefaults.bubbleRadiusFor(bubbleCornerRadiusDp),
        LocalChatWallpaperId provides (chatWallpaperId ?: ChatWallpaperOption.DEFAULT.id),
        LocalChatWallpaperCustomColor provides chatWallpaperCustomColorArgb?.let { Color(it) },
        LocalChatWallpaperImageUri provides chatWallpaperImageUri,
        LocalChatWallpaperGradient provides chatWallpaperGradient,
        LocalChatListThreeLine provides chatListThreeLine,
        LocalOutgoingBubbleColor provides chatBubbleColorArgb?.let { Color(it) },
    ) {
        content(effectiveCompoundLight, effectiveCompoundDark)
    }
}
