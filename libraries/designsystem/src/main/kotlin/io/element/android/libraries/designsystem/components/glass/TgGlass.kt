/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.glass

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.LarpgramColors
import io.element.android.compound.tokens.larpgramColors

/**
 * Правка форка: «жидкое стекло» Larpgram (редизайн V1 final, `tokens.json` → `glass`). Под панелью
 * размытый контент (haze), сверху полупрозрачная заливка и тонкая светлая обводка.
 *
 * Сначала было сделано для экрана чата (отсюда имя), теперь тот же источник размытия ставят и
 * список чатов, и профиль.
 */
val LocalChatGlassState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Правка форка: сколько места снизу экрана чата занимают плавающие панели (поле ввода). Лента
 * уходит под них, поэтому добавляет это к нижнему отступу списка и поднимает кнопки прокрутки.
 */
val LocalChatBottomOverlayHeight = compositionLocalOf { 0.dp }

/** Правка форка: высота плавающей шапки чата сверху (вместе со статус-баром). */
val LocalChatTopOverlayHeight = compositionLocalOf { 0.dp }

/** Which glass fill to use, from the lightest to the most opaque. */
enum class TgGlassTint {
    Surface,
    SurfaceStrong,
    Card,
    Menu,
}

object TgGlassDefaults {
    /** Скругление пилюли поля ввода (`radius.composer`). */
    val inputRadius: Dp = 26.dp

    /** Высота поля ввода (`sizes.composerHeight`). */
    val inputHeight: Dp = 52.dp

    /** Отступ пилюли от краёв экрана и от низа. */
    val inputSideMargin: Dp = 8.dp
    val inputBottomMargin: Dp = 8.dp

    val blurRadius: Dp = 26.dp

    /** Opaque panel colour: the `fallback` of the glass tokens. */
    @Composable
    fun panelColor(): Color = larpgramColors.glassFallback

    @Composable
    fun tintColor(tint: TgGlassTint): Color = larpgramColors.tint(tint)

    @Composable
    internal fun style(tint: TgGlassTint = TgGlassTint.Surface): HazeStyle {
        val colors = larpgramColors
        return HazeStyle(
            backgroundColor = colors.background,
            tint = HazeTint(colors.tint(tint)),
            blurRadius = blurRadius,
            noiseFactor = 0f,
        )
    }

    /** Цвет иконок на стекле. */
    @Composable
    fun iconColor(): Color = ElementTheme.colors.iconPrimary
}

private fun LarpgramColors.tint(tint: TgGlassTint): Color = when (tint) {
    TgGlassTint.Surface -> glassSurface
    TgGlassTint.SurfaceStrong -> glassSurfaceStrong
    TgGlassTint.Card -> glassCard
    TgGlassTint.Menu -> glassMenu
}

/**
 * Правка форка: blur is skipped in battery saver (and in previews), the glass then falls back to
 * the opaque `glass.fallback` colour, as the design asks for reduced effects.
 *
 * Also skipped below Android 12 (decided 2026-10-31, docs/perf.md): there haze blurs with
 * RenderScript on the CPU during the draw on the main thread, and on the Honor (Android 10) that
 * was ~70% of the lag of the message menu, most of the lag while typing and a third of it when a
 * chat opens; next to a progressive panel it also kept an idle chat list redrawing at 60 fps.
 */
@Composable
fun rememberGlassBlurEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val context = LocalContext.current
    return remember(context) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        power?.isPowerSaveMode != true
    }
}

/**
 * Правка форка: стеклянная подложка по форме [shape]. Если размывать нечего (нет
 * [LocalChatGlassState], превью, энергосбережение) — непрозрачная заливка `glass.fallback`.
 *
 * @param tint how opaque the glass fill is.
 * @param overrideTint a custom fill instead of [tint] (e.g. the accent-tinted search button).
 * @param elevation shadow under the panel, 0 for none.
 */
@Composable
fun Modifier.tgGlass(
    shape: Shape,
    tint: TgGlassTint = TgGlassTint.Surface,
    overrideTint: Color? = null,
    borderColor: Color? = null,
    elevation: Dp = 0.dp,
): Modifier {
    val hazeState = LocalChatGlassState.current?.takeIf { rememberGlassBlurEnabled() }
    val colors = larpgramColors
    val fill = overrideTint ?: colors.tint(tint)
    val stroke = if (borderColor != null) {
        Brush.verticalGradient(listOf(borderColor, borderColor))
    } else {
        // 1 dp light border, brighter on top (`glass.topHighlight`).
        Brush.verticalGradient(listOf(colors.glassTopHighlight, colors.glassBorder.copy(alpha = colors.glassBorder.alpha * 0.6f)))
    }
    val shadowModifier = if (elevation > 0.dp) {
        Modifier.shadow(elevation = elevation, shape = shape, ambientColor = colors.glassShadow, spotColor = colors.glassShadow)
    } else {
        Modifier
    }
    val fillModifier = if (hazeState != null) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = colors.background,
                tint = HazeTint(fill),
                blurRadius = TgGlassDefaults.blurRadius,
                noiseFactor = 0f,
            ),
        ) {
            // Haze blurs below Android 12 only when asked to; rememberGlassBlurEnabled() keeps it off there.
            blurEnabled = true
        }
    } else if (overrideTint != null) {
        // The custom tint is the point of the surface (accent button): keep it, over the fallback.
        Modifier.background(colors.glassFallback).background(fill)
    } else {
        Modifier.background(colors.glassFallback)
    }
    return this
        .then(shadowModifier)
        .clip(shape)
        .then(fillModifier)
        .border(width = 1.dp, brush = stroke, shape = shape)
}

/**
 * Правка форка: размытая подложка под статус-баром и шапкой — сверху сильнее, к низу сходит на нет
 * (прогрессивный блюр haze), поверх `glass.headerFade`. Без источника размытия — просто градиент.
 */
@Composable
fun Modifier.tgTopFade(): Modifier {
    val hazeState = LocalChatGlassState.current?.takeIf { rememberGlassBlurEnabled() }
    val colors = larpgramColors
    return if (hazeState != null) {
        this.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = colors.background,
                tint = HazeTint(colors.headerFadeTop.copy(alpha = colors.headerFadeTop.alpha * 0.7f)),
                blurRadius = 20.dp,
                noiseFactor = 0f,
            ),
        ) {
            blurEnabled = true
            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
            // Маска гасит и блюр, и заливку к низу — иначе у подложки была бы видимая граница.
            mask = Brush.verticalGradient(0.6f to Color.Black, 1f to Color.Transparent)
        }
    } else {
        this.background(Brush.verticalGradient(listOf(colors.headerFadeTop, colors.headerFadeBottom.copy(alpha = 0f))))
    }
}
