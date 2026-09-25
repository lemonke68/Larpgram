/*
 * Правка форка: аватар «Избранного» — белая закладка на голубом, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.ui.saved

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/**
 * Закладка в сетке 24×24: прямоугольник со скруглённым верхом и вырезом снизу. Одна и та же
 * фигура для картинки-аватара комнаты и для иконки в интерфейсе.
 */
object SavedMessagesAvatar {
    // Цвета аватара Saved Messages в TG (`avatar_backgroundSaved`, сверху вниз).
    private const val TOP_COLOR = 0xFF69BFFA.toInt()
    private const val BOTTOM_COLOR = 0xFF3D9DE0.toInt()
    private const val SIZE_PX = 512

    /** PNG для аватара комнаты: квадрат, клиенты сами обрезают его в круг. */
    fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, SIZE_PX.toFloat(), TOP_COLOR, BOTTOM_COLOR, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, SIZE_PX.toFloat(), SIZE_PX.toFloat(), background)
        // Закладка занимает ~45% стороны, по центру.
        val scale = SIZE_PX * 0.45f / 24f
        val offset = (SIZE_PX - 24f * scale) / 2f
        canvas.save()
        canvas.translate(offset, offset)
        canvas.scale(scale, scale)
        canvas.drawPath(bookmarkPath(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        canvas.restore()
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun bookmarkPath() = Path().apply {
        moveTo(7f, 2f)
        lineTo(17f, 2f)
        quadTo(19f, 2f, 19f, 4f)
        lineTo(19f, 22f)
        lineTo(12f, 17f)
        lineTo(5f, 22f)
        lineTo(5f, 4f)
        quadTo(5f, 2f, 7f, 2f)
        close()
    }

    /** Та же закладка как иконка интерфейса (пункт «Избранное» в настройках). */
    val icon: ImageVector by lazy {
        ImageVector.Builder(
            name = "SavedMessages",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 2f)
            lineTo(17f, 2f)
            quadTo(19f, 2f, 19f, 4f)
            lineTo(19f, 22f)
            lineTo(12f, 17f)
            lineTo(5f, 22f)
            lineTo(5f, 4f)
            quadTo(5f, 2f, 7f, 2f)
            close()
        }.build()
    }
}
