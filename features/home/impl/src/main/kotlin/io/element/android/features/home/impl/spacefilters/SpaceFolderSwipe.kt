/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spacefilters

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.element.android.libraries.designsystem.animation.TgMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

// Порог горизонтального свайпа для переключения папки. Меньше — легко переключить случайно
// при диагональном скролле; больше — приходится тянуть далеко. Замерено на глаз, правит юзер.
private val FOLDER_SWIPE_THRESHOLD = 56.dp

// Тянуть туда, где соседней папки нет, можно, но с сопротивлением.
private const val EDGE_RESISTANCE = 0.2f

// Сколько ждать новый список после переключения папки, прежде чем показать его как есть.
private const val CONTENT_WAIT_MS = 400L

// По тапу лента трогается, когда новый список уже пришёл и отрисован, но ждёт не дольше этого.
// На Honor (Android 10) список «Все» с аватарками собирается ~200 мс: если ехать раньше, сборка
// списка съедает кадры анимации.
private const val TAP_CONTENT_WAIT_MS = 350L

// Кадры после прихода списка: первый его собирает, второй рисует.
private const val FRAMES_TO_DRAW_CONTENT = 2

/** Индекс текущей вкладки в ленте (0 = «Все», далее папки в порядке ленты). */
internal fun SpaceFiltersState.currentTabIndex(): Int {
    val current = selectedFilter() ?: return 0
    return availableFilters().indexOfFirst { it.spaceRoom.roomId == current.spaceRoom.roomId } + 1
}

/** Can the strip move one step in [direction] (1 = the next folder on the right, -1 = left)? */
fun canSwitchSpaceFilter(state: SpaceFiltersState, direction: Int): Boolean {
    val size = state.availableFilters().size
    if (size == 0) return false
    return state.currentTabIndex() + direction in 0..size
}

/** Selects the folder at [index] of the strip (0 = «Все»). */
internal fun selectSpaceFilterIndex(state: SpaceFiltersState, index: Int) {
    val filters = state.availableFilters()
    if (index !in 0..filters.size || index == state.currentTabIndex()) return
    when (state) {
        is SpaceFiltersState.Unselected -> {
            if (index > 0) state.eventSink(SpaceFiltersEvent.Unselected.SelectFilter(filters[index - 1]))
        }
        is SpaceFiltersState.Selected -> {
            if (index == 0) {
                state.eventSink(SpaceFiltersEvent.Selected.ClearSelection)
            } else {
                state.eventSink(SpaceFiltersEvent.Selected.SelectFilter(filters[index - 1]))
            }
        }
        else -> Unit
    }
}

/**
 * Larpgram: переключение «папок» (Matrix Spaces) на соседнюю, как свайпом в Telegram.
 * Направление 1 — следующая папка справа по ленте; -1 — предыдущая слева.
 */
fun switchSpaceFilter(state: SpaceFiltersState, direction: Int) {
    selectSpaceFilterIndex(state, state.currentTabIndex() + direction)
}

/**
 * Shared between the folder pager ([SpaceFolderSwipe]) and the folder strip
 * ([SpaceFolderPillsView]): the strip asks the pager to switch, and the selected pill follows the
 * pager's position, including mid-swipe.
 */
@Stable
class SpaceFolderPagerState {
    internal var positionProvider: (() -> Float)? = null
    internal var request: ((Int) -> Unit)? = null

    /**
     * Where the strip is, in folder indices (0 = «Все»), fractional while the pages move. Null
     * without a pager. Read it in the layout or draw phase: it changes every frame of a swipe.
     */
    val position: Float? get() = positionProvider?.invoke()

    /** Slides to the folder at [index]. False when no pager is attached (then select directly). */
    fun selectIndex(index: Int): Boolean {
        val request = request ?: return false
        request(index)
        return true
    }
}

@Composable
fun rememberSpaceFolderPagerState(): SpaceFolderPagerState = remember { SpaceFolderPagerState() }

/**
 * The chat list as a pager of folders, like Telegram: the pages of neighbouring folders sit side
 * by side. A tap on a folder to the right slides the strip left and the other way round; a swipe
 * over the list drags the pages with the finger, and past [FOLDER_SWIPE_THRESHOLD] it settles on
 * the neighbour, otherwise it springs back.
 *
 * The presenter only ever has the list of the selected folder, so the page being left is a frozen
 * snapshot of the list, and the live list (already filtered for the new folder) comes in next to
 * it. The live page stays hidden until its content has changed, so the old list never shows twice.
 *
 * Swipes that start on the edge zones of a row are taken by the row itself (chat actions); only
 * the middle and empty space reach this pager. `draggable(Horizontal)` leaves the vertical scroll
 * of the list alone.
 *
 * @param contentKey changes when the list content changes (the room list state); tells when the
 * new folder's list has arrived.
 */
@Composable
fun SpaceFolderSwipe(
    state: SpaceFiltersState,
    modifier: Modifier = Modifier,
    pagerState: SpaceFolderPagerState = rememberSpaceFolderPagerState(),
    contentKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val latest = rememberUpdatedState(state)
    val latestKey = rememberUpdatedState(contentKey)
    if (state.availableFilters().isEmpty()) {
        SideEffect {
            pagerState.positionProvider = null
            pagerState.request = null
        }
        Box(modifier) { content() }
        return
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val thresholdPx = with(density) { FOLDER_SWIPE_THRESHOLD.toPx() }
    val layer = rememberGraphicsLayer()
    var widthPx by remember { mutableFloatStateOf(0f) }
    // Translation of the page being left (or of the only page at rest).
    val offset = remember { Animatable(0f) }
    // Folder of the page being left: the snapshot shows it while the strip moves.
    var base by remember { mutableIntStateOf(state.currentTabIndex()) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    // False while the live list still shows the previous folder after a switch.
    var liveReady by remember { mutableStateOf(true) }
    // A tap animation or a gesture owns the strip.
    var busy by remember { mutableStateOf(false) }
    var gestureIgnored by remember { mutableStateOf(false) }

    fun selected() = latest.value.currentTabIndex()
    fun lastIndex() = latest.value.availableFilters().size

    suspend fun capture(): ImageBitmap? = try {
        when {
            layer.size.width <= 0 -> null
            // The Picture-based snapshot of Android 9-11 drops child layers and hardware bitmaps
            // (all the avatars): render through a hardware canvas instead.
            Build.VERSION.SDK_INT in Build.VERSION_CODES.Q until Build.VERSION_CODES.S ->
                layer.captureOnHardwareCanvas(density, layoutDirection)
            else -> layer.toImageBitmap()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    // Switches the presenter to [index]; the live page shows once the new list has arrived.
    fun switchLive(index: Int) {
        if (index == selected()) return
        val keyBefore = latestKey.value
        liveReady = false
        selectSpaceFilterIndex(latest.value, index)
        scope.launch {
            withTimeoutOrNull(CONTENT_WAIT_MS) { snapshotFlow { latestKey.value }.first { it !== keyBefore } }
            liveReady = true
        }
    }

    suspend fun settle() {
        withTimeoutOrNull(CONTENT_WAIT_MS) { snapshotFlow { liveReady }.first { it } }
        base = selected()
        snapshot = null
        offset.snapTo(0f)
        liveReady = true
        busy = false
    }

    // Remaining part of a full page move, at least a third of the tap duration.
    fun durationFor(remainingPx: Float): Int {
        val w = widthPx.takeIf { it > 0f } ?: return TgMotion.FOLDER_MS
        return (TgMotion.FOLDER_MS * (remainingPx / w)).roundToInt().coerceIn(TgMotion.FOLDER_MS / 3, TgMotion.FOLDER_MS)
    }

    SideEffect {
        pagerState.request = { target ->
            if (!busy && target != selected() && target in 0..lastIndex()) {
                busy = true
                scope.launch {
                    base = selected()
                    offset.snapTo(0f)
                    snapshot = capture()
                    switchLive(target)
                    withTimeoutOrNull(TAP_CONTENT_WAIT_MS) {
                        snapshotFlow { liveReady }.first { it }
                        repeat(FRAMES_TO_DRAW_CONTENT) { withFrameNanos { } }
                    }
                    val dir = sign((target - base).toFloat())
                    offset.animateTo(-dir * widthPx, TgMotion.tween(TgMotion.FOLDER_MS))
                    settle()
                }
            }
        }
        pagerState.positionProvider = {
            val sel = selected()
            val w = widthPx
            when {
                w <= 0f -> sel.toFloat()
                sel == base -> (base - offset.value / w).coerceIn(0f, lastIndex().toFloat())
                else -> base + (sel - base) * (abs(offset.value) / w).coerceIn(0f, 1f)
            }
        }
    }

    // A folder change that did not come from here (back clears the folder, a folder disappeared):
    // nothing to slide from, just follow.
    val current = state.currentTabIndex()
    SideEffect {
        if (!busy && current != base) base = current
    }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    if (gestureIgnored) return@rememberDraggableState
                    val w = widthPx.takeIf { it > 0f } ?: return@rememberDraggableState
                    val cur = offset.value
                    val dir = if (cur + delta < 0f) 1 else -1
                    val hasNeighbour = base + dir in 0..lastIndex()
                    val next = (cur + if (hasNeighbour) delta else delta * EDGE_RESISTANCE).coerceIn(-w, w)
                    scope.launch { offset.snapTo(next) }
                    // Once the snapshot is ready, the live list becomes the neighbour on the side
                    // the finger pulls from (or the folder itself again).
                    if (snapshot != null) {
                        val nextDir = if (next < 0f) 1 else -1
                        val want = if (next != 0f && base + nextDir in 0..lastIndex()) base + nextDir else base
                        switchLive(want)
                    }
                },
                onDragStarted = {
                    gestureIgnored = busy
                    if (!gestureIgnored) {
                        busy = true
                        base = selected()
                        offset.snapTo(0f)
                        scope.launch {
                            val bitmap = capture()
                            // The finger may already be gone: then the stop handler settles alone.
                            if (busy) snapshot = bitmap
                        }
                    }
                },
                onDragStopped = {
                    if (gestureIgnored) {
                        gestureIgnored = false
                    } else {
                        val cur = offset.value
                        val dir = if (cur < 0f) 1 else -1
                        val w = widthPx
                        val passed = abs(cur) >= thresholdPx && base + dir in 0..lastIndex()
                        if (passed) {
                            // A fast flick can end before the snapshot is ready: switch now, the
                            // page being left then shows an empty strip behind it.
                            if (selected() == base) switchLive(base + dir)
                            offset.animateTo(-dir * w, TgMotion.tween(durationFor(w - abs(cur))))
                        } else {
                            offset.animateTo(0f, TgMotion.tween(durationFor(abs(cur))))
                            switchLive(base)
                        }
                        settle()
                    }
                },
            ),
    ) {
        val sel = state.currentTabIndex()
        val liveShift = sign((sel - base).toFloat())
        val showSnapshot = snapshot != null
        Box(
            Modifier
                .graphicsLayer {
                    translationX = offset.value + if (sel == base) 0f else liveShift * size.width
                    // While the snapshot covers the folder being left, the live page is the
                    // neighbour: hidden at rest and until its list has arrived.
                    alpha = if (!liveReady || (showSnapshot && sel == base)) 0f else 1f
                }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
        ) {
            content()
        }
        if (showSnapshot) {
            Spacer(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val bitmap = snapshot ?: return@drawBehind
                        translate(left = offset.value) { drawImage(bitmap) }
                    }
            )
        }
    }
}

/**
 * Draws this layer on a hardware canvas of an [ImageReader] surface and wraps the result, like
 * Compose's own snapshot for Android 5-8. Unlike the Picture path of Android 9+, it keeps child
 * layers and hardware bitmaps.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private suspend fun GraphicsLayer.captureOnHardwareCanvas(density: Density, layoutDirection: LayoutDirection): ImageBitmap? {
    val width = size.width
    val height = size.height
    val reader = ImageReader.newInstance(
        width,
        height,
        PixelFormat.RGBA_8888,
        1,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    try {
        val image = suspendCancellableCoroutine<Image?> { continuation ->
            reader.setOnImageAvailableListener(
                { source -> if (continuation.isActive) continuation.resume(source.acquireLatestImage()) },
                Handler(Looper.getMainLooper()),
            )
            val surface = reader.surface
            val canvas = surface.lockHardwareCanvas()
            try {
                canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                CanvasDrawScope().draw(density, layoutDirection, Canvas(canvas), Size(width.toFloat(), height.toFloat())) {
                    drawLayer(this@captureOnHardwareCanvas)
                }
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        } ?: return null
        return image.use { frame ->
            frame.hardwareBuffer?.use { buffer ->
                Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))?.asImageBitmap()
            }
        }
    } finally {
        reader.close()
    }
}
