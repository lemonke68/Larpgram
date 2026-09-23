/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.media

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import coil3.Bitmap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.colors.TgAvatarPalette
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text

@ContributesBinding(AppScope::class)
class DefaultInitialsAvatarBitmapGenerator : InitialsAvatarBitmapGenerator {
    /**
     * Generates a bitmap for an avatar with no URL, using the initials from the [AvatarData].
     * @param size The size of the bitmap to generate, in pixels.
     * @param avatarData The [AvatarData] containing the initials and other information.
     * @param useDarkTheme Whether the theme is dark.
     * @param fontSizePercentage The percentage of the avatar size to use for the font size.
     */
    override fun generateBitmap(
        size: Int,
        avatarData: AvatarData,
        useDarkTheme: Boolean,
        fontSizePercentage: Float,
    ): Bitmap? {
        if (avatarData.url != null) {
            // This generator is only for initials avatars, not for avatars with URLs
            return null
        }

        // Правка форка: заглушка как в Telegram — вертикальный градиент по id и белые инициалы.
        val gradient = TgAvatarPalette.gradient(avatarData.id, isDark = useDarkTheme)

        val bitmap = createBitmap(size, size)
        Canvas(bitmap).run {
            val backgroundPaint = Paint().apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    size.toFloat(),
                    gradient.top.toArgb(),
                    gradient.bottom.toArgb(),
                    Shader.TileMode.CLAMP,
                )
            }
            drawRect(0f, 0f, size.toFloat(), size.toFloat(), backgroundPaint)
            val letters = avatarData.initials

            val textPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = size * fontSizePercentage // Adjust text size relative to the avatar size
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            drawText(
                letters,
                size / 2f,
                size.toFloat() / 2 - (textPaint.descent() + textPaint.ascent()) / 2,
                textPaint
            )
        }

        return bitmap
    }
}

@Composable
@PreviewsDayNight
internal fun InitialsAvatarBitmapGeneratorPreview() = ElementPreview {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val generator = remember { DefaultInitialsAvatarBitmapGenerator() }
        repeat(6) { index ->
            val avatarData = remember { AvatarData(id = index.toString(), name = Char('0'.code + index).toString(), size = AvatarSize.IncomingCall) }
            val isLightTheme = ElementTheme.isLightTheme
            val bitmap = remember(isLightTheme) {
                generator.generateBitmap(
                    size = 512,
                    avatarData = avatarData,
                    useDarkTheme = !isLightTheme,
                )?.asImageBitmap()
            }
            bitmap?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.size(48.dp))
            } ?: Text("No avatar generated")
        }
    }
}
