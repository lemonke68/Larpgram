/*
 * Правка форка: обои переписки.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import coil3.compose.rememberAsyncImagePainter
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.theme.ChatWallpaperOption
import io.element.android.libraries.designsystem.theme.LocalChatWallpaperCustomColor
import io.element.android.libraries.designsystem.theme.LocalChatWallpaperGradient
import io.element.android.libraries.designsystem.theme.LocalChatWallpaperId
import io.element.android.libraries.designsystem.theme.LocalChatWallpaperImageUri
import io.element.android.libraries.designsystem.theme.chatWallpaperBackground

/**
 * Чем закрашен фон переписки.
 *
 * Юзер потом сможет менять обои сам — на сплошной цвет или на свою фотографию, — поэтому
 * источник фона сразу вынесен в тип, а не прибит к одному паттерну. Добавить вариант
 * «своя картинка» = дописать сюда ветку, а не переделывать таймлайн.
 */
@Immutable
sealed interface ChatWallpaper {
    /** Сплошная заливка. */
    data class Solid(val color: Color) : ChatWallpaper

    /**
     * Рисунок поверх заливки: паттерн из макета или своя фотография юзера.
     *
     * [drawable] растягивается так, чтобы закрыть всю площадь без искажения пропорций —
     * обои в макете нарисованы на целый экран, а не мелкой плиткой. [tint] перекрашивает
     * рисунок, поэтому один паттерн годится обеим темам; для фотографии он не нужен.
     */
    data class Image(
        val background: Color,
        @DrawableRes val drawable: Int,
        val tint: Color? = null,
    ) : ChatWallpaper

    /** Своя фотография пользователя, адресуется content-URI. Грузится Coil'ом, кроется по площади. */
    data class Photo(val uri: String) : ChatWallpaper

    /** Двухцветный линейный градиент под углом [angleDeg] (0 = слева направо, 90 = сверху вниз). */
    data class Gradient(val start: Color, val end: Color, val angleDeg: Int) : ChatWallpaper
}

/**
 * Обои по умолчанию. Отдельная функция, потому что позже она станет читать выбор юзера.
 *
 * Паттерн выгружен из макета в альфу и перекрашивается на месте, поэтому файл один на обе
 * темы: в светлой рисунок белый и заметный, в тёмной еле уловимый.
 */
@Composable
fun defaultChatWallpaper(): ChatWallpaper = ChatWallpaper.Image(
    background = ElementTheme.colors.chatWallpaperBackground,
    drawable = R.drawable.chat_wallpaper_pattern,
    tint = Color.White.copy(alpha = if (ElementTheme.isLightTheme) 0.45f else 0.05f),
)

/**
 * Обои по выбору юзера (`LocalChatWallpaperId`, раздаётся в `ElementThemeApp`). Сплошные пресеты —
 * фиксированный цвет; вариант «паттерн» отдаётся в [defaultChatWallpaper] (тема-зависимый рисунок).
 */
@Composable
fun selectedChatWallpaper(): ChatWallpaper {
    val id = LocalChatWallpaperId.current
    // Своя фотография: URI хранится отдельным пре­фом, id-маркер = CUSTOM_IMAGE_ID.
    if (id == ChatWallpaperOption.CUSTOM_IMAGE_ID) {
        LocalChatWallpaperImageUri.current?.let { return ChatWallpaper.Photo(it) }
    }
    // Двухцветный градиент: спека хранится отдельным пре­фом, id-маркер = CUSTOM_GRADIENT_ID.
    if (id == ChatWallpaperOption.CUSTOM_GRADIENT_ID) {
        LocalChatWallpaperGradient.current?.let {
            return ChatWallpaper.Gradient(Color(it.startArgb), Color(it.endArgb), it.angleDeg)
        }
    }
    // Пипетка: произвольный цвет юзера хранится отдельным пре­фом, id-маркер = CUSTOM_ID.
    if (id == ChatWallpaperOption.CUSTOM_ID) {
        LocalChatWallpaperCustomColor.current?.let { return ChatWallpaper.Solid(it) }
    }
    val option = ChatWallpaperOption.fromId(id)
    return when (val color = option.solidColor) {
        null -> defaultChatWallpaper()
        else -> ChatWallpaper.Solid(color)
    }
}

/**
 * Рисует обои под содержимым. Именно `drawBehind`, а не `background`: паттерн замащивается
 * вручную и не должен попадать в клиппинг дочерних элементов.
 */
@Composable
fun Modifier.chatWallpaper(wallpaper: ChatWallpaper): Modifier = when (wallpaper) {
    is ChatWallpaper.Solid -> drawBehind { drawRect(wallpaper.color) }
    is ChatWallpaper.Image -> {
        val painter = painterResource(wallpaper.drawable)
        val filter = wallpaper.tint?.let { ColorFilter.tint(it) }
        drawBehind {
            drawRect(wallpaper.background)
            drawCovering(painter, filter)
        }
    }
    is ChatWallpaper.Photo -> {
        // Фотография пользователя: Coil грузит из content-URI, кроем по площади. Пока не
        // загрузилось (или ошибка) — под ней тема-зависимый фон, drawCovering молчит на
        // неопределённом intrinsic-size.
        val background = ElementTheme.colors.chatWallpaperBackground
        val painter = rememberAsyncImagePainter(model = wallpaper.uri)
        drawBehind {
            drawRect(background)
            drawCovering(painter, null)
        }
    }
    is ChatWallpaper.Gradient -> drawBehind {
        // Угол в направление: 0° = слева направо, растём по часовой. Линию тянем через центр так,
        // чтобы покрыть весь прямоугольник.
        val rad = Math.toRadians(wallpaper.angleDeg.toDouble())
        val dx = kotlin.math.cos(rad).toFloat()
        val dy = kotlin.math.sin(rad).toFloat()
        val half = (kotlin.math.abs(dx) * size.width + kotlin.math.abs(dy) * size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val brush = Brush.linearGradient(
            colors = listOf(wallpaper.start, wallpaper.end),
            start = Offset(cx - dx * half, cy - dy * half),
            end = Offset(cx + dx * half, cy + dy * half),
        )
        drawRect(brush)
    }
}

/** Рисует [painter] по центру, увеличив до покрытия всей площади и сохранив пропорции. */
private fun DrawScope.drawCovering(painter: Painter, colorFilter: ColorFilter?) {
    val intrinsic = painter.intrinsicSize
    if (!intrinsic.isSpecified || intrinsic.width <= 0f || intrinsic.height <= 0f) return
    val scale = maxOf(size.width / intrinsic.width, size.height / intrinsic.height)
    val target = Size(intrinsic.width * scale, intrinsic.height * scale)
    translate(
        left = (size.width - target.width) / 2f,
        top = (size.height - target.height) / 2f,
    ) {
        with(painter) { draw(size = target, colorFilter = colorFilter) }
    }
}
