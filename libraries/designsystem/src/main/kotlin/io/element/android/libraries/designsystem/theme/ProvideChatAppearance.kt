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
import io.element.android.libraries.preferences.api.store.ChatAppearance
import io.element.android.libraries.preferences.api.store.ChatAppearanceStore

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
    val appearance by remember(chatAppearanceStore) {
        chatAppearanceStore.getAppearanceFlow()
    }.collectAsState(initial = ChatAppearance())
    val chatWallpaperGradientSpec = appearance.chatWallpaperGradient
    val chatWallpaperGradient = remember(chatWallpaperGradientSpec) {
        ChatWallpaperGradient.parse(chatWallpaperGradientSpec)
    }
    // A chosen accent rebuilds the whole accent family on top of the themed palettes.
    val accentColor = appearance.chatAccentColorArgb?.let { Color(it) }
    val effectiveCompoundLight = remember(compoundLight, accentColor) {
        accentColor?.let { compoundLight.withLarpgramAccent(it, isLight = true) } ?: compoundLight
    }
    val effectiveCompoundDark = remember(compoundDark, accentColor) {
        accentColor?.let { compoundDark.withLarpgramAccent(it, isLight = false) } ?: compoundDark
    }
    CompositionLocalProvider(
        LocalMessageTextScale provides ChatAppearanceDefaults.textScaleFor(appearance.messageTextSizeSp),
        LocalChatBubbleRadius provides ChatAppearanceDefaults.bubbleRadiusFor(appearance.bubbleCornerRadiusDp),
        LocalChatWallpaperId provides (appearance.chatWallpaperId ?: ChatWallpaperOption.DEFAULT.id),
        LocalChatWallpaperCustomColor provides appearance.chatWallpaperCustomColorArgb?.let { Color(it) },
        LocalChatWallpaperImageUri provides appearance.chatWallpaperImageUri,
        LocalChatWallpaperGradient provides chatWallpaperGradient,
        LocalChatListThreeLine provides appearance.chatListThreeLine,
        LocalOutgoingBubbleColor provides appearance.chatBubbleColorArgb?.let { Color(it) },
    ) {
        content(effectiveCompoundLight, effectiveCompoundDark)
    }
}
