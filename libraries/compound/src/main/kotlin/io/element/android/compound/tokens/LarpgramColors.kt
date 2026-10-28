/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.compound.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.compoundColorsDark
import io.element.android.compound.tokens.generated.compoundColorsLight

/**
 * Larpgram palette, redesign "V1 final" (2026-10-29).
 *
 * Values come from `design/redesign/handoff/tokens.json`. The generated Compound tokens are left
 * untouched: we only override the semantic colors on top of them, so upstream regeneration keeps
 * working. Tokens that Compound has no slot for (glass, glow, wallpaper, bubbles…) live in
 * [LarpgramColors], read through [larpgramColors].
 *
 * Accent stays purple (user decision 2026-08-16); V1 final moves it to `#6D4BD8` / `#7C5CE6`,
 * and accent-coloured text uses a separate, more contrasting shade.
 */
object LarpgramPalette {
    val accentLight = Color(0xFF6D4BD8)
    val accentLightHovered = Color(0xFF5F3FC8)
    val accentLightPressed = Color(0xFF5234B4)
    val accentLightSubtle = Color(0xFFEEE9FB)
    val accentTextLight = Color(0xFF5B3CC4)

    val accentDark = Color(0xFF7C5CE6)
    val accentDarkHovered = Color(0xFF8E72EA)
    val accentDarkPressed = Color(0xFFA189EE)
    val accentDarkSubtle = Color(0xFF2B2245)
    val accentTextDark = Color(0xFFB9A0FF)

    /** Message bubbles. Incoming ones are translucent: the blurred wallpaper shows through. */
    val bubbleFromMeLight = Color(0xFFEDE6FF)
    val bubbleFromOtherLight = Color(0xADFFFFFF)
    val bubbleFromMeDark = Color(0xFF5B3FC4)
    val bubbleFromOtherDark = Color(0x9E28223A)

    /** Flat base of the chat wallpaper (under the gradient and the dots). */
    val chatWallpaperLight = Color(0xFFE6DDF7)
    val chatWallpaperDark = Color(0xFF100E16)

    /** Ticks: accent on the pale light bubble, light violet on the solid dark one. */
    val deliveredLight = accentLight
    val deliveredDark = Color(0xFFD7CAFF)
}

/**
 * Larpgram tokens without a Compound slot (`tokens.json` → `colors`, `glass`).
 */
@Immutable
data class LarpgramColors(
    val isLight: Boolean,
    val background: Color,
    /** Soft radial spots behind glass: top-left, right-middle, bottom-left. */
    val backgroundGlow: List<Color>,
    val accent: Color,
    val accentText: Color,
    val accentSoft: Color,
    val badgeMuted: Color,
    val draft: Color,
    val danger: Color,
    val warning: Color,
    val online: Color,
    val folderChipSelected: Color,
    val tabSelected: Color,
    val dim: Color,
    val wallpaperStops: List<Pair<Float, Color>>,
    /** Dark wallpaper glows (light theme uses the plain gradient). */
    val wallpaperGlow: List<Color>,
    val wallpaperDots: Color,
    val bubbleIncoming: Color,
    val bubbleOutgoing: Color,
    val bubbleOutgoingText: Color,
    val bubbleOutgoingMeta: Color,
    val bubbleIncomingMeta: Color,
    val quoteBackground: Color,
    val reaction: Color,
    val reactionMine: Color,
    val dateChip: Color,
    val waveformPlayed: Color,
    val waveformUnplayed: Color,
    val senderNames: List<Color>,
    val glassSurface: Color,
    val glassSurfaceStrong: Color,
    val glassCard: Color,
    val glassMenu: Color,
    val glassBorder: Color,
    val glassTopHighlight: Color,
    val glassShadow: Color,
    val searchButton: Color,
    val searchButtonBorder: Color,
    val headerFadeTop: Color,
    val headerFadeBottom: Color,
    /** Opaque stand-in for glass when blur is off (power saving, previews). */
    val glassFallback: Color,
)

val larpgramExtraColorsLight = LarpgramColors(
    isLight = true,
    background = Color(0xFFFBFAFE),
    backgroundGlow = listOf(Color(0x387C5CE6), Color(0x1FC2477A), Color(0x1F3CAAC8)),
    accent = LarpgramPalette.accentLight,
    accentText = LarpgramPalette.accentTextLight,
    accentSoft = Color(0x1A6D4BD8),
    badgeMuted = Color(0xFFB3ACC4),
    draft = Color(0xFFD93A45),
    danger = Color(0xFFD12C3A),
    warning = Color(0xFF9A6200),
    online = Color(0xFF2FAE5E),
    folderChipSelected = Color(0xFFFFFFFF),
    tabSelected = Color(0x1F6D4BD8),
    dim = Color(0x381E143C),
    wallpaperStops = listOf(0f to Color(0xFFEFE9FB), 0.45f to Color(0xFFD9CCF4), 0.75f to Color(0xFFE9DDF6), 1f to Color(0xFFCFC0F0)),
    wallpaperGlow = emptyList(),
    wallpaperDots = Color(0x1A5B3CC4),
    bubbleIncoming = LarpgramPalette.bubbleFromOtherLight,
    bubbleOutgoing = LarpgramPalette.bubbleFromMeLight,
    bubbleOutgoingText = Color(0xFF15121D),
    bubbleOutgoingMeta = Color(0xFF6D4BD8),
    bubbleIncomingMeta = Color(0xFF8E88A0),
    quoteBackground = Color(0x126D4BD8),
    reaction = Color(0x1A6D4BD8),
    reactionMine = LarpgramPalette.accentLight,
    dateChip = Color(0x523C2878),
    waveformPlayed = Color(0xFF6D4BD8),
    waveformUnplayed = Color(0xFFCFC6E4),
    senderNames = listOf(Color(0xFFC2336E), Color(0xFFA35C00), Color(0xFF3D7FD6), Color(0xFF2E8C80), Color(0xFF7C5CE6)),
    glassSurface = Color(0x75FFFFFF),
    glassSurfaceStrong = Color(0x85FFFFFF),
    glassCard = Color(0x80FFFFFF),
    glassMenu = Color(0x94FFFFFF),
    glassBorder = Color(0xF2FFFFFF),
    glassTopHighlight = Color(0xE6FFFFFF),
    glassShadow = Color(0x243C2878),
    searchButton = Color(0xCC6D4BD8),
    searchButtonBorder = Color(0x80FFFFFF),
    headerFadeTop = Color(0xD9FBFAFE),
    headerFadeBottom = Color(0x4DFBFAFE),
    glassFallback = Color(0xFFF3F0FA),
)

val larpgramExtraColorsDark = LarpgramColors(
    isLight = false,
    background = Color(0xFF15131B),
    backgroundGlow = listOf(Color(0x577C5CE6), Color(0x29C2477A), Color(0x243CAAC8)),
    accent = LarpgramPalette.accentDark,
    accentText = LarpgramPalette.accentTextDark,
    accentSoft = Color(0x2E8C6CF0),
    badgeMuted = Color(0xFF4A4460),
    draft = Color(0xFFF07178),
    danger = Color(0xFFFF7A85),
    warning = Color(0xFFF2C46D),
    online = Color(0xFF4FCE7D),
    folderChipSelected = Color(0x21FFFFFF),
    tabSelected = Color(0x388C6CF0),
    dim = Color(0x7308060E),
    wallpaperStops = listOf(0f to Color(0xFF100E16), 1f to Color(0xFF100E16)),
    wallpaperGlow = listOf(Color(0x387C5CE6), Color(0x2E5A3CC8)),
    wallpaperDots = Color(0x14B9A0FF),
    bubbleIncoming = LarpgramPalette.bubbleFromOtherDark,
    bubbleOutgoing = LarpgramPalette.bubbleFromMeDark,
    bubbleOutgoingText = Color(0xFFFFFFFF),
    bubbleOutgoingMeta = Color(0xFFD7CAFF),
    bubbleIncomingMeta = Color(0xFF8A83A0),
    quoteBackground = Color(0x0FFFFFFF),
    reaction = Color(0x338C6CF0),
    reactionMine = LarpgramPalette.accentDark,
    dateChip = Color(0x52000000),
    waveformPlayed = Color(0xFFB9A0FF),
    waveformUnplayed = Color(0xFF4A4460),
    senderNames = listOf(Color(0xFFFF9DBB), Color(0xFFFFC46B), Color(0xFF8FD3FF), Color(0xFF6FE0CC), Color(0xFFB9A0FF)),
    glassSurface = Color(0x662C2640),
    glassSurfaceStrong = Color(0x7A2C2640),
    glassCard = Color(0x6B2C2640),
    glassMenu = Color(0x8C2C2640),
    glassBorder = Color(0x1AFFFFFF),
    glassTopHighlight = Color(0x24FFFFFF),
    glassShadow = Color(0x59000000),
    searchButton = Color(0x9E7C5CE6),
    searchButtonBorder = Color(0x38FFFFFF),
    headerFadeTop = Color(0xCC15131B),
    headerFadeBottom = Color(0x4015131B),
    glassFallback = Color(0xFF24202F),
)

/** Larpgram-only tokens for the current theme. */
val larpgramColors: LarpgramColors
    @Composable
    @ReadOnlyComposable
    get() = if (ElementTheme.isLightTheme) larpgramExtraColorsLight else larpgramExtraColorsDark

val larpgramColorsLight: SemanticColors = compoundColorsLight.copy(
    bgAccentRest = LarpgramPalette.accentLight,
    bgAccentHovered = LarpgramPalette.accentLightHovered,
    bgAccentPressed = LarpgramPalette.accentLightPressed,
    bgAccentSelected = LarpgramPalette.accentLight.copy(alpha = 0.12f),
    bgAccentSubtle = LarpgramPalette.accentLightSubtle,
    borderAccentPrimary = LarpgramPalette.accentLight,
    borderAccentSubtle = LarpgramPalette.accentLight.copy(alpha = 0.40f),
    borderFocused = LarpgramPalette.accentLight,
    iconAccentPrimary = LarpgramPalette.accentLight,
    iconAccentTertiary = LarpgramPalette.accentLight,
    textActionAccent = LarpgramPalette.accentTextLight,
    textLinkExternal = LarpgramPalette.accentTextLight,
    // Unread badge: сплошной акцент с белым числом, как в Telegram.
    bgBadgeAccent = LarpgramPalette.accentLight,
    textBadgeAccent = Color(0xFFFFFFFF),
    gradientActionStop1 = Color(0xFFB69DF8),
    gradientActionStop2 = Color(0xFF8E6FE6),
    gradientActionStop3 = LarpgramPalette.accentLight,
    gradientActionStop4 = LarpgramPalette.accentLightPressed,
    gradientSubtleStop1 = LarpgramPalette.accentLight.copy(alpha = 0.16f),
    gradientSubtleStop2 = LarpgramPalette.accentLight.copy(alpha = 0.12f),
    gradientSubtleStop3 = LarpgramPalette.accentLight.copy(alpha = 0.08f),
    gradientSubtleStop4 = LarpgramPalette.accentLight.copy(alpha = 0.05f),
    gradientSubtleStop5 = LarpgramPalette.accentLight.copy(alpha = 0.02f),
    // Surfaces and text: violet-tinted neutrals of V1 final.
    bgCanvasDefault = Color(0xFFFBFAFE),
    bgSubtlePrimary = Color(0xFFE6E2EF),
    bgSubtleSecondary = Color(0xFFF3F0FA),
    bgCanvasDisabled = Color(0xFFF3F0FA),
    textPrimary = Color(0xFF15121D),
    textSecondary = Color(0xFF6E6880),
    iconPrimary = Color(0xFF15121D),
    iconSecondary = Color(0xFF6E6880),
    iconTertiary = Color(0xFF8E88A0),
    separatorPrimary = Color(0xFFECE9F2),
    separatorSecondary = Color(0xFFECE9F2),
    textCriticalPrimary = Color(0xFFD12C3A),
    iconCriticalPrimary = Color(0xFFD12C3A),
)

val larpgramColorsDark: SemanticColors = compoundColorsDark.copy(
    bgAccentRest = LarpgramPalette.accentDark,
    bgAccentHovered = LarpgramPalette.accentDarkHovered,
    bgAccentPressed = LarpgramPalette.accentDarkPressed,
    bgAccentSelected = Color(0x388C6CF0),
    bgAccentSubtle = LarpgramPalette.accentDarkSubtle,
    borderAccentPrimary = LarpgramPalette.accentDark,
    borderAccentSubtle = LarpgramPalette.accentDark.copy(alpha = 0.40f),
    borderFocused = LarpgramPalette.accentDark,
    iconAccentPrimary = LarpgramPalette.accentDark,
    iconAccentTertiary = LarpgramPalette.accentDark,
    textActionAccent = LarpgramPalette.accentTextDark,
    textLinkExternal = LarpgramPalette.accentTextDark,
    bgBadgeAccent = LarpgramPalette.accentDark,
    textBadgeAccent = Color(0xFFFFFFFF),
    gradientActionStop1 = Color(0xFFB69DF8),
    gradientActionStop2 = Color(0xFF9A80EC),
    gradientActionStop3 = LarpgramPalette.accentDark,
    gradientActionStop4 = Color(0xFF5B3FC4),
    gradientSubtleStop1 = LarpgramPalette.accentDark.copy(alpha = 0.16f),
    gradientSubtleStop2 = LarpgramPalette.accentDark.copy(alpha = 0.12f),
    gradientSubtleStop3 = LarpgramPalette.accentDark.copy(alpha = 0.08f),
    gradientSubtleStop4 = LarpgramPalette.accentDark.copy(alpha = 0.05f),
    gradientSubtleStop5 = LarpgramPalette.accentDark.copy(alpha = 0.02f),
    bgCanvasDefault = Color(0xFF15131B),
    bgSubtlePrimary = Color(0xFF2E293C),
    bgSubtleSecondary = Color(0xFF24202F),
    bgCanvasDisabled = Color(0xFF1C1924),
    textPrimary = Color(0xFFF4F1FA),
    textSecondary = Color(0xFF9C95AE),
    iconPrimary = Color(0xFFF4F1FA),
    iconSecondary = Color(0xFF9C95AE),
    iconTertiary = Color(0xFF8A83A0),
    separatorPrimary = Color(0x12FFFFFF),
    separatorSecondary = Color(0x12FFFFFF),
    textCriticalPrimary = Color(0xFFFF7A85),
    iconCriticalPrimary = Color(0xFFFF7A85),
)

/**
 * Larpgram: rebuild the whole accent family from a single user-chosen [accent] color, on top of
 * an already-Larpgram-themed [SemanticColors]. This is the runtime counterpart of the static
 * overrides above (which pin the purple brand accent): the accent picker feeds a color in, and this
 * derives the hovered/pressed/subtle shades and the action gradients from it so the app stays
 * coherent. Non-accent tokens (surfaces, text, borders) are left as they are.
 *
 * Shades are derived by nudging the accent toward black (light theme) or white (dark theme), the
 * same direction the hand-tuned static shades move, so an arbitrary accent reads correctly in both.
 */
fun SemanticColors.withLarpgramAccent(accent: Color, isLight: Boolean): SemanticColors {
    val shadeTarget = if (isLight) Color.Black else Color.White
    val hovered = lerp(accent, shadeTarget, 0.12f)
    val pressed = lerp(accent, shadeTarget, 0.26f)
    val subtleBg = if (isLight) lerp(accent, Color.White, 0.90f) else lerp(accent, Color.Black, 0.80f)
    val gradientLight1 = lerp(accent, Color.White, 0.34f)
    val gradientLight2 = lerp(accent, Color.White, 0.16f)
    return copy(
        bgAccentRest = accent,
        bgAccentHovered = hovered,
        bgAccentPressed = pressed,
        bgAccentSelected = accent.copy(alpha = 0.20f),
        bgAccentSubtle = subtleBg,
        borderAccentPrimary = accent,
        borderAccentSubtle = accent.copy(alpha = 0.40f),
        iconAccentPrimary = accent,
        iconAccentTertiary = accent,
        textActionAccent = accent,
        textLinkExternal = accent,
        bgBadgeAccent = accent,
        textBadgeAccent = Color(0xFFFFFFFF),
        gradientActionStop1 = gradientLight1,
        gradientActionStop2 = gradientLight2,
        gradientActionStop3 = accent,
        gradientActionStop4 = pressed,
        gradientSubtleStop1 = accent.copy(alpha = 0.16f),
        gradientSubtleStop2 = accent.copy(alpha = 0.12f),
        gradientSubtleStop3 = accent.copy(alpha = 0.08f),
        gradientSubtleStop4 = accent.copy(alpha = 0.05f),
        gradientSubtleStop5 = accent.copy(alpha = 0.02f),
    )
}
