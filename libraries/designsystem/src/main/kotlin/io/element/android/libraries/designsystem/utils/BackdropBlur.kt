/*
 * Правка форка: размытый снимок экрана под всплывающими поверхностями (запись кружочка,
 * меню долгого нажатия на сообщении). На Android 10 системного блюра нет, поэтому свой.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.designsystem.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Во сколько раз уменьшается снимок экрана перед размытием.
 *
 * Уменьшение и есть половина размытия: PixelCopy масштабирует поверхность окна билинейно,
 * поэтому мелкие детали пропадают ещё до нашего фильтра. Заодно это делает сам фильтр
 * почти бесплатным: на Kirin 710 размывать приходится картинку примерно 135x300, а не
 * 1080x2340.
 */
private const val DOWNSCALE = 8

/** Радиус размытия в пикселях уменьшенной картинки. */
private const val BLUR_RADIUS = 6

/** Проходов box-фильтра: трёх хватает, чтобы результат не отличался от гауссова на глаз. */
private const val BLUR_PASSES = 3

/**
 * Снимок окна под диалогом, размытый.
 *
 * Почему PixelCopy, а не `decorView.draw(Canvas)`: окно рисуется аппаратно, и в software
 * Canvas оно отдаёт пустоту — именно на этом развалилась первая попытка 2026-08-15, и на
 * телефоне размытие просто не появлялось. PixelCopy читает готовую поверхность окна и
 * поэтому видит ровно то же, что человек.
 *
 * Диалог живёт в собственном окне, поэтому в снимок он не попадает: копируется поверхность
 * окна активити, то есть чат под записью.
 *
 * До API 26 нужной перегрузки PixelCopy нет (у нас minSdk 24), там возвращается null и
 * экран остаётся с обычной тёмной подложкой.
 */
@Composable
fun rememberBlurredBackdrop(enabled: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(enabled) {
        if (!enabled || isPreview) {
            backdrop = null
            return@LaunchedEffect
        }
        backdrop = captureBlurred(context)
    }
    return backdrop
}

/**
 * Резкий снимок прямоугольной области окна активити, в полном разрешении.
 *
 * Нужен меню долгого нажатия: фон под ним размыт, но само нажатое сообщение должно
 * остаться чётким. Снимаем именно окно активити, поэтому размытый слой и меню, лежащие в
 * своих окнах поверх, в снимок не попадают — в кадре чистый чат.
 *
 * `left`/`top`/`right`/`bottom` — в пикселях окна (то же, что даёт `boundsInWindow`).
 */
@Composable
fun rememberSharpRegion(left: Int, top: Int, right: Int, bottom: Int, enabled: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    var region by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(enabled, left, top, right, bottom) {
        if (!enabled || isPreview) {
            region = null
            return@LaunchedEffect
        }
        region = captureRegion(context, left, top, right, bottom)
    }
    return region
}

private suspend fun captureRegion(context: Context, left: Int, top: Int, right: Int, bottom: Int): ImageBitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val window = context.findActivity()?.window ?: return null
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) return null

    return runCatching {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        suspendCancellableCoroutine { continuation ->
            PixelCopy.request(
                window,
                android.graphics.Rect(left, top, right, bottom),
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        continuation.resume(bitmap.asImageBitmap())
                    } else {
                        Timber.w("PixelCopy области вернул $result")
                        bitmap.recycle()
                        continuation.resume(null)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }.onFailure { Timber.w(it, "не удалось снять область для меню долгого нажатия") }
        .getOrNull()
}

private suspend fun captureBlurred(context: Context): ImageBitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val window = context.findActivity()?.window ?: return null
    val decorView = window.peekDecorView() ?: return null
    val width = decorView.width / DOWNSCALE
    val height = decorView.height / DOWNSCALE
    if (width <= 0 || height <= 0) return null

    val shot = runCatching { capture(window, width, height) }
        .onFailure { Timber.w(it, "не удалось снять фон под записью кружочка") }
        .getOrNull()
        ?: return null

    return withContext(Dispatchers.Default) {
        shot.blurInPlace()
        shot.asImageBitmap()
    }
}

/**
 * PixelCopy сам вписывает поверхность окна в переданный битмап, поэтому уменьшение
 * достаётся даром: отдельного `createScaledBitmap` не нужно.
 */
private suspend fun capture(window: Window, width: Int, height: Int): Bitmap? {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        PixelCopy.request(
            window,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    continuation.resume(bitmap)
                } else {
                    Timber.w("PixelCopy вернул $result")
                    bitmap.recycle()
                    continuation.resume(null)
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }
}

/**
 * Box-фильтр в несколько проходов: горизонтальный и вертикальный по очереди.
 *
 * Гаусс тут не нужен и стоил бы дороже: три прохода box-фильтра от него неотличимы, а
 * RenderScript выпилен из Android, RenderEffect же появился только в API 31.
 */
private fun Bitmap.blurInPlace() {
    val width = width
    val height = height
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val scratch = IntArray(pixels.size)

    repeat(BLUR_PASSES) {
        boxBlur(pixels, scratch, width, height)
        // Второй проход идёт по строкам повёрнутой картинки, то есть по столбцам исходной.
        boxBlur(scratch, pixels, height, width)
    }
    setPixels(pixels, 0, width, 0, 0, width, height)
}

/** Размывает по строкам и заодно транспонирует, чтобы второй проход шёл так же. */
@Suppress("NestedBlockDepth")
private fun boxBlur(source: IntArray, destination: IntArray, width: Int, height: Int) {
    val window = BLUR_RADIUS * 2 + 1
    for (y in 0 until height) {
        val row = y * width
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        // Стартовое окно: края повторяются, иначе картинка темнеет по периметру.
        for (i in -BLUR_RADIUS..BLUR_RADIUS) {
            val pixel = source[row + i.coerceIn(0, width - 1)]
            alpha += pixel ushr 24 and 0xFF
            red += pixel shr 16 and 0xFF
            green += pixel shr 8 and 0xFF
            blue += pixel and 0xFF
        }
        for (x in 0 until width) {
            destination[x * height + y] =
                (alpha / window shl 24) or (red / window shl 16) or (green / window shl 8) or (blue / window)
            val outgoing = source[row + (x - BLUR_RADIUS).coerceIn(0, width - 1)]
            val incoming = source[row + (x + BLUR_RADIUS + 1).coerceIn(0, width - 1)]
            alpha += (incoming ushr 24 and 0xFF) - (outgoing ushr 24 and 0xFF)
            red += (incoming shr 16 and 0xFF) - (outgoing shr 16 and 0xFF)
            green += (incoming shr 8 and 0xFF) - (outgoing shr 8 and 0xFF)
            blue += (incoming and 0xFF) - (outgoing and 0xFF)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
