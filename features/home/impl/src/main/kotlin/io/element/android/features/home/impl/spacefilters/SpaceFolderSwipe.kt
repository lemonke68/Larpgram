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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

// Запомненная страница папки закрывает живой список, пока тот не пришёл, но не дольше этого.
private const val CACHED_PAGE_HOLD_MS = 2_000L

// Первый тап по папке, которой ещё нет в памяти: лента трогается, когда новый список пришёл и
// отрисован, но ждёт не дольше этого. На Honor (Android 10) список «Все» с аватарками
// собирается ~200 мс: если ехать раньше, сборка списка съедает кадры анимации.
private const val TAP_CONTENT_WAIT_MS = 350L

// Кадры после прихода списка: первый его собирает, второй рисует.
private const val FRAMES_TO_DRAW_CONTENT = 2

// Через сколько после остановки ленты собирать запомненные страницы соседних папок.
private const val NEIGHBOURS_WARM_UP_DELAY_MS = 600L

// Сколько кадров запомненная страница ещё закрывает пришедший живой список (Honor собирает
// список «Все» с аватарками за 2-3 кадра).
private const val FRAMES_TO_SWAP_PAGES = 4

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
 * Shared between the folder pager ([SpaceFolderSwipe]), the folder strip ([SpaceFolderPillsView])
 * and the page cache of the chat list: the strip asks the pager to switch, the selected pill
 * follows the pager's position (also mid-swipe), and the cache learns which folder the live list
 * really shows.
 */
@Stable
class SpaceFolderPagerState {
    internal var positionProvider: (() -> Float)? = null
    internal var request: ((Int) -> Unit)? = null

    /**
     * Folder whose list the presenter shows for sure (its content arrived after the last switch),
     * or null right after a switch. Only then may the live list be remembered as that folder's page.
     */
    var freshIndex: Int? by mutableStateOf(null)
        internal set

    /** True only until the selected folder's response arrives or its bounded wait expires. */
    var isContentPending: Boolean by mutableStateOf(false)
        internal set

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
 * The presenter only ever has the list of the selected folder. Pages of folders seen before come
 * from [cachedPage] (the list as it was last shown): they are ready at once, so the strip moves
 * without waiting, and the remembered page stays in place until the live list of that folder has
 * arrived. A folder never opened yet falls back to a frozen snapshot of the page being left, with
 * the live list (already filtered for the new folder) coming in next to it once it has arrived.
 *
 * Swipes that start on the edge zones of a row are taken by the row itself (chat actions); only
 * the middle and empty space reach this pager. `draggable(Horizontal)` leaves the vertical scroll
 * of the list alone.
 *
 * @param contentKey changes when the list content changes, null while the list is not there yet
 * (loading, or the empty list a folder switch goes through); tells when the new folder's list has
 * arrived.
 * @param cachedPage draws the remembered page of a folder, for the indices [hasCachedPage] accepts.
 */
@Composable
fun SpaceFolderSwipe(
    state: SpaceFiltersState,
    modifier: Modifier = Modifier,
    pagerState: SpaceFolderPagerState = rememberSpaceFolderPagerState(),
    contentKey: Any? = null,
    hasCachedPage: (Int) -> Boolean = { false },
    cachedPage: @Composable (Int) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val latest = rememberUpdatedState(state)
    val latestKey = rememberUpdatedState(contentKey)
    val latestHasCached = rememberUpdatedState(hasCachedPage)
    if (state.availableFilters().isEmpty()) {
        SideEffect {
            pagerState.positionProvider = null
            pagerState.request = null
            pagerState.freshIndex = state.currentTabIndex()
            pagerState.isContentPending = false
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
    // Translation of the folder being left: animated, or following the finger while it drags.
    val offset = remember { Animatable(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Folder of the page being left (the only page at rest).
    var base by remember { mutableIntStateOf(state.currentTabIndex()) }
    // Folder coming in next to it, and whether its remembered page is the one shown.
    var target by remember { mutableStateOf<Int?>(null) }
    var targetCached by remember { mutableStateOf(false) }
    // After a move to a remembered page: it stays as the page until the live list has arrived.
    var cachedAsBase by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    // False while the live list still shows the previous folder after a switch.
    var liveReady by remember { mutableStateOf(true) }
    var switchToken by remember { mutableIntStateOf(0) }
    // Folder last asked of the presenter, and one more asked while that was still on its way.
    var requested by remember { mutableIntStateOf(state.currentTabIndex()) }
    var queued by remember { mutableStateOf<Int?>(null) }
    // A tap animation or a gesture owns the strip.
    var busy by remember { mutableStateOf(false) }
    var gestureIgnored by remember { mutableStateOf(false) }
    var awaitingExternal by remember { mutableStateOf(false) }
    var warmNeighbours by remember { mutableStateOf(true) }

    fun selected() = latest.value.currentTabIndex()
    fun lastIndex() = latest.value.availableFilters().size
    fun currentOffset() = if (dragging) dragX else offset.value
    fun isCached(index: Int) = index in 0..lastIndex() && latestHasCached.value(index)

    suspend fun capture(): ImageBitmap? = try {
        when {
            // The live list is hidden behind a remembered page: nothing true to capture.
            layer.size.width <= 0 || cachedAsBase -> null
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
    fun sendSwitch(index: Int) {
        val keyBefore = latestKey.value
        val token = ++switchToken
        liveReady = false
        pagerState.isContentPending = true
        pagerState.freshIndex = null
        requested = index
        selectSpaceFilterIndex(latest.value, index)
        // Behind a remembered page the wait can be long: the presenter takes 0.5-1 s on the Honor.
        val waitMs = if (cachedAsBase) CACHED_PAGE_HOLD_MS else CONTENT_WAIT_MS
        scope.launch {
            val arrived = withTimeoutOrNull(waitMs) {
                snapshotFlow { latestKey.value }.first { it != null && it != keyBefore }
            } != null
            if (token == switchToken) {
                liveReady = true
                pagerState.isContentPending = false
                if (arrived && selected() == index) pagerState.freshIndex = index
            }
        }
    }

    // The events depend on the presenter's current state, so a switch is only sent once the
    // previous one has been applied; a later wish waits in [queued].
    fun switchLive(index: Int) {
        when {
            index == requested -> queued = null
            selected() != requested -> {
                queued = index
                liveReady = false
            }
            else -> sendSwitch(index)
        }
    }

    suspend fun awaitLive(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { snapshotFlow { liveReady }.first { it } }
    }

    // Back to rest on [newBase], whatever happened (also when the animation was cancelled). The
    // folder is passed in: right after a switch the presenter may not have reported it yet.
    suspend fun reset(newBase: Int) = withContext(NonCancellable) {
        dragging = false
        target = null
        targetCached = false
        snapshot = null
        offset.snapTo(0f)
        base = newBase
        busy = false
    }

    // The strip has arrived on [to]: from now on it is the page at rest.
    suspend fun commit(to: Int) {
        // The new neighbours are composed only once the strip has rested: composing one now would
        // stall the frames right after the move.
        warmNeighbours = false
        scope.launch {
            delay(NEIGHBOURS_WARM_UP_DELAY_MS)
            snapshotFlow { cachedAsBase || busy }.first { !it }
            warmNeighbours = true
        }
        if (targetCached) {
            // The remembered page is on screen already: switch the presenter behind it and drop it
            // once the live list of that folder is there.
            cachedAsBase = true
            switchLive(to)
            reset(to)
            scope.launch {
                awaitLive(CACHED_PAGE_HOLD_MS)
                // The live list has its rooms now, but composing them takes a few frames: keep
                // the remembered page over it until it is drawn.
                repeat(FRAMES_TO_SWAP_PAGES) { withFrameNanos { } }
                cachedAsBase = false
            }
        } else {
            if (requested != to) switchLive(to)
            awaitLive(CONTENT_WAIT_MS)
            reset(to)
        }
    }

    // Which list the presenter should show mid-swipe: the neighbour only when it has no
    // remembered page and the snapshot covers the folder being left.
    fun syncLiveWithTarget() {
        val t = target
        val desired = if (t != null && !targetCached && snapshot != null) t else base
        switchLive(desired)
    }

    // Remaining part of a full page move, at least a third of the tap duration.
    fun durationFor(remainingPx: Float): Int {
        val w = widthPx.takeIf { it > 0f } ?: return TgMotion.FOLDER_MS
        return (TgMotion.FOLDER_MS * (remainingPx / w)).roundToInt().coerceIn(TgMotion.FOLDER_MS / 3, TgMotion.FOLDER_MS)
    }

    SideEffect {
        pagerState.request = { to ->
            if (!busy && to != requested && to in 0..lastIndex()) {
                busy = true
                scope.launch {
                    try {
                        base = requested
                        target = to
                        targetCached = isCached(to)
                        offset.snapTo(0f)
                        if (!targetCached) {
                            snapshot = capture()
                            switchLive(to)
                            withTimeoutOrNull(TAP_CONTENT_WAIT_MS) {
                                snapshotFlow { liveReady }.first { it }
                                repeat(FRAMES_TO_DRAW_CONTENT) { withFrameNanos { } }
                            }
                        }
                        val dir = sign((to - base).toFloat())
                        offset.animateTo(-dir * widthPx, TgMotion.tween(TgMotion.FOLDER_MS))
                        commit(to)
                    } finally {
                        if (busy) reset(requested)
                    }
                }
            }
        }
        pagerState.positionProvider = {
            val w = widthPx
            val o = currentOffset()
            val t = target
            when {
                w <= 0f -> base.toFloat()
                t != null -> base + (t - base) * (abs(o) / w).coerceIn(0f, 1f)
                else -> (base - o / w).coerceIn(0f, lastIndex().toFloat())
            }
        }
    }

    // A folder change that did not come from here (back clears the folder, a folder disappeared):
    // nothing to slide from, just follow, and wait for that folder's list before trusting it.
    val current = state.currentTabIndex()
    LaunchedEffect(current) {
        val next = queued
        when {
            next != null && current == requested -> {
                queued = null
                switchLive(next)
            }
            !busy && !cachedAsBase && current != requested -> {
                requested = current
                base = current
                pagerState.freshIndex = null
                awaitingExternal = true
            }
        }
    }
    LaunchedEffect(contentKey) {
        if (contentKey == null) return@LaunchedEffect
        when {
            awaitingExternal -> {
                awaitingExternal = false
                pagerState.freshIndex = selected()
            }
            !busy && liveReady -> pagerState.freshIndex = selected()
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    if (gestureIgnored || !dragging) return@rememberDraggableState
                    val w = widthPx.takeIf { it > 0f } ?: return@rememberDraggableState
                    val dir = if (dragX + delta < 0f) 1 else -1
                    val hasNeighbour = base + dir in 0..lastIndex()
                    // Applied right here, not in a coroutine: a late update could otherwise land
                    // after the release and cancel the settling animation.
                    dragX = (dragX + if (hasNeighbour) delta else delta * EDGE_RESISTANCE).coerceIn(-w, w)
                    val nextDir = if (dragX < 0f) 1 else -1
                    val nextTarget = (base + nextDir).takeIf { dragX != 0f && it in 0..lastIndex() }
                    if (nextTarget != target) {
                        target = nextTarget
                        targetCached = nextTarget != null && isCached(nextTarget)
                    }
                    syncLiveWithTarget()
                },
                onDragStarted = {
                    gestureIgnored = busy
                    if (!gestureIgnored) {
                        busy = true
                        // A remembered page still standing in for the live list stays the page
                        // being left: hiding it here would leave the strip empty.
                        base = requested
                        target = null
                        dragX = 0f
                        dragging = true
                        // A neighbour never opened yet needs the snapshot of this page.
                        val needsSnapshot = !cachedAsBase && listOf(base - 1, base + 1).any { it in 0..lastIndex() && !isCached(it) }
                        if (needsSnapshot) {
                            scope.launch {
                                val bitmap = capture()
                                if (dragging) {
                                    snapshot = bitmap
                                    syncLiveWithTarget()
                                }
                            }
                        }
                    }
                },
                onDragStopped = {
                    if (gestureIgnored) {
                        gestureIgnored = false
                    } else {
                        try {
                            offset.snapTo(dragX)
                            dragging = false
                            val t = target
                            val o = offset.value
                            val w = widthPx
                            if (t != null && abs(o) >= thresholdPx) {
                                val dir = sign((t - base).toFloat())
                                // A fast flick can end before the snapshot is ready: switch now.
                                if (!targetCached && requested == base) switchLive(t)
                                offset.animateTo(-dir * w, TgMotion.tween(durationFor(w - abs(o))))
                                commit(t)
                            } else {
                                offset.animateTo(0f, TgMotion.tween(durationFor(abs(o))))
                                if (requested != base) {
                                    switchLive(base)
                                    awaitLive(CONTENT_WAIT_MS)
                                }
                                reset(base)
                            }
                        } finally {
                            if (busy) reset(requested)
                        }
                    }
                },
            ),
    ) {
        // Positions are worked out in the draw phase from the same state as the offset: computed in
        // composition they lag a frame behind, and that frame showed the page off-screen.
        fun moveDir(): Int = target?.let { sign((it - base).toFloat()).toInt() } ?: 0
        val t = target
        // Remembered pages: the neighbours are composed off-screen once the strip has been at rest
        // for a moment (composing a long list takes a few frames on old phones), the target of a
        // move is composed when the move starts.
        val pages = buildList {
            if (warmNeighbours) {
                add(base - 1)
                add(base + 1)
            }
            if (t != null) add(t)
            if (cachedAsBase) add(base)
        }.distinct().filter { it in 0..lastIndex() && hasCachedPage(it) }.sorted()
        pages.forEach { index ->
            key(index) {
                Box(
                    Modifier.graphicsLayer {
                        val side = sign((index - base).toFloat())
                        val visible = when {
                            index == base -> cachedAsBase
                            target == null -> true
                            side.toInt() == moveDir() -> index == target && targetCached
                            else -> true
                        }
                        translationX = currentOffset() + side * size.width
                        alpha = if (visible) 1f else 0f
                    }
                ) {
                    cachedPage(index)
                }
            }
        }
        Box(
            Modifier
                .graphicsLayer {
                    val sel = latest.value.currentTabIndex()
                    val liveIsBase = sel == base && snapshot == null && !cachedAsBase
                    val liveIsTarget = target != null && sel == target && sel != base && !targetCached
                    translationX = currentOffset() + if (liveIsTarget) moveDir() * size.width else 0f
                    alpha = if (liveReady && (liveIsBase || liveIsTarget)) 1f else 0f
                }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
        ) {
            content()
        }
        if (snapshot != null && !cachedAsBase) {
            Spacer(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val bitmap = snapshot ?: return@drawBehind
                        translate(left = currentOffset()) { drawImage(bitmap) }
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
