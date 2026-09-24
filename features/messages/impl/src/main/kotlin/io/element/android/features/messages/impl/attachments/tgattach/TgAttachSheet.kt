/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.tgattach

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.androidutils.system.openAppSettingsPage
import io.element.android.libraries.designsystem.components.glass.LocalChatGlassState
import io.element.android.libraries.designsystem.components.glass.tgGlass
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Правка форка: что пользователь сделал в меню вложений Telegram. */
@Immutable
internal sealed interface TgAttachAction {
    data object Dismiss : TgAttachAction
    data class Send(val media: List<GalleryMedia>, val caption: String?, val compress: Boolean) : TgAttachAction
    data class Preview(val media: List<GalleryMedia>) : TgAttachAction
    data object CameraPhoto : TgAttachAction
    data object CameraVideo : TgAttachAction
    data object SystemGallery : TgAttachAction
    data object Files : TgAttachAction
    data object Location : TgAttachAction
    data object Poll : TgAttachAction
    data object TextFormatting : TgAttachAction
}

/** Спрашивали ли уже доступ к галерее за жизнь процесса: сами просим только при первом открытии. */
private var galleryPermissionAsked = false

/**
 * Правка форка: меню вложений Telegram (`ChatAttachAlert` + `ChatAttachAlertPhotoLayout`).
 *
 * Своя шторка поверх чата, а не Material: снизу всегда видна пилюля вкладок (или строка подписи),
 * а шторка тянется от «половины экрана» до полного. Сетка 3 колонки с зазором 2dp, первая плитка —
 * живая камера на две строки, выбор с номерами, выбранная плитка сжимается до 0.787.
 */
@Composable
internal fun TgAttachSheet(
    isVisible: Boolean,
    canShareLocation: Boolean,
    enableTextFormatting: Boolean,
    onAction: (TgAttachAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isShown by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) {
        if (isVisible) isShown = true
    }
    if (!isShown) return
    TgAttachSheetContent(
        closeRequested = !isVisible,
        canShareLocation = canShareLocation,
        enableTextFormatting = enableTextFormatting,
        onAction = onAction,
        onClosed = { isShown = false },
        modifier = modifier,
    )
}

@Composable
private fun TgAttachSheetContent(
    closeRequested: Boolean,
    canShareLocation: Boolean,
    enableTextFormatting: Boolean,
    onAction: (TgAttachAction) -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val isLight = ElementTheme.isLightTheme
    val sheetColor = if (isLight) Color.White else Color(0xFF1C1C1D)
    val statusBarTop = WindowInsets.statusBars.getTop(density).toFloat()

    // --- доступ и данные -------------------------------------------------------------------------
    var access by remember { mutableStateOf(GalleryPermissions.access(context)) }
    var deniedForever by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = GalleryPermissions.access(context)
        val activity = context.findActivity()
        deniedForever = access == GalleryAccess.None &&
            activity != null &&
            !activity.shouldShowRequestPermissionRationale(GalleryPermissions.main)
        reloadKey++
    }
    fun requestAccess() {
        if (deniedForever) {
            context.openAppSettingsPage()
        } else {
            permissionLauncher.launch(GalleryPermissions.requested)
        }
    }
    LaunchedEffect(Unit) {
        if (access == GalleryAccess.None && !galleryPermissionAsked) {
            galleryPermissionAsked = true
            requestAccess()
        }
    }
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            val newAccess = GalleryPermissions.access(context)
            if (newAccess != access || newAccess == GalleryAccess.Partial) {
                access = newAccess
                reloadKey++
            }
        }
    }
    val media by produceState<List<GalleryMedia>?>(initialValue = null, access, reloadKey) {
        value = if (access == GalleryAccess.None) emptyList() else GalleryMediaStore.load(context)
    }

    val selection = remember { mutableStateListOf<GalleryMedia>() }
    var caption by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullHeight = constraints.maxHeight.toFloat()
        val partialTop = fullHeight * PARTIAL_TOP_FRACTION
        val scope = rememberCoroutineScope()
        var offsetPx by remember { mutableFloatStateOf(fullHeight) }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        var isClosing by remember { mutableStateOf(false) }
        val currentOnAction by rememberUpdatedState(onAction)
        val currentOnClosed by rememberUpdatedState(onClosed)

        fun animateTo(target: Float, velocity: Float = 0f, fast: Boolean = false, then: () -> Unit = {}) {
            settleJob?.cancel()
            settleJob = scope.launch {
                animate(
                    initialValue = offsetPx,
                    targetValue = target,
                    initialVelocity = velocity,
                    animationSpec = if (fast) tween(durationMillis = 200) else spring(stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> offsetPx = value }
                then()
            }
        }

        fun close(notify: Boolean) {
            if (isClosing) return
            isClosing = true
            animateTo(fullHeight, fast = !notify) {
                if (notify) currentOnAction(TgAttachAction.Dismiss)
                currentOnClosed()
            }
        }

        fun dragBy(delta: Float): Float {
            if (isClosing) return 0f
            settleJob?.cancel()
            val old = offsetPx
            offsetPx = (old + delta).coerceIn(0f, fullHeight)
            return offsetPx - old
        }

        fun settle(velocity: Float) {
            if (isClosing) return
            val anchors = listOf(0f, partialTop, fullHeight)
            val target = when {
                velocity < -FLING_VELOCITY -> anchors.lastOrNull { it < offsetPx - 1f } ?: 0f
                velocity > FLING_VELOCITY -> anchors.firstOrNull { it > offsetPx + 1f } ?: fullHeight
                else -> anchors.minBy { abs(it - offsetPx) }
            }
            if (target == fullHeight) close(notify = true) else animateTo(target, velocity)
        }

        LaunchedEffect(Unit) { animateTo(partialTop) }
        LaunchedEffect(closeRequested) {
            if (closeRequested) close(notify = false)
        }
        BackHandler { close(notify = true) }

        val nestedScroll = remember(fullHeight, partialTop) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Палец вверх: сначала раскрываем шторку, потом крутим сетку.
                    return if (available.y < 0f && offsetPx > 0f) Offset(0f, dragBy(available.y)) else Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    // Сетка упёрлась в начало, палец вниз: тянем шторку.
                    return if (available.y > 0f && source == NestedScrollSource.UserInput) Offset(0f, dragBy(available.y)) else Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (available.y < 0f && offsetPx > 0f) {
                        settle(available.y)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (offsetPx != 0f && offsetPx != partialTop) settle(available.y)
                    return available
                }
            }
        }

        // Затемнение до «половины», дальше шторка просто уезжает.
        val openFraction = (1f - (offsetPx - partialTop) / (fullHeight - partialTop)).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA * openFraction))
                .pointerInput(Unit) { detectTapGestures { close(notify = true) } },
        )

        val sheetHaze = rememberHazeState()
        val isExpanded = offsetPx <= 1f
        // Строка «Выбрано N» появляется над сеткой, а не сдвигает её: шторка растёт вверх на её
        // высоту, плитки остаются под пальцем (как в Telegram).
        val titleHeight by animateDpAsState(
            targetValue = if (isExpanded || selection.isNotEmpty()) TITLE_HEIGHT else 0.dp,
            label = "attach_title_height",
        )
        val sheetTop = (offsetPx - with(density) { titleHeight.toPx() }).coerceAtLeast(0f)
        val expandFraction = (1f - sheetTop / max(statusBarTop, 1f)).coerceIn(0f, 1f)
        val cornerRadius = lerp(SHEET_RADIUS.value, 0f, expandFraction).dp
        val headerTopPaddingPx = (statusBarTop - sheetTop).coerceAtLeast(0f)
        val barHeight = TAB_BAR_HEIGHT + 16.dp
        val navBarBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }

        Column(
            modifier = Modifier
                .offset { IntOffset(0, sheetTop.roundToInt()) }
                .fillMaxWidth()
                .height(with(density) { fullHeight.toDp() })
                .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
                .background(sheetColor)
                .nestedScroll(nestedScroll),
        ) {
            SheetHeader(
                selection = selection,
                isExpanded = isExpanded,
                titleHeight = titleHeight,
                topPadding = with(density) { headerTopPaddingPx.toDp() },
                onDrag = ::dragBy,
                onDragStopped = ::settle,
                onClose = { close(notify = true) },
                onPreviewSelection = { onAction(TgAttachAction.Preview(selection.toList())) },
                onSendUncompressed = {
                    onAction(TgAttachAction.Send(selection.toList(), caption.trim().ifEmpty { null }, compress = false))
                },
            )
            GalleryGrid(
                access = access,
                deniedForever = deniedForever,
                media = media,
                selection = selection,
                bottomPadding = barHeight + navBarBottom,
                onRequestAccess = ::requestAccess,
                onAction = onAction,
                modifier = Modifier
                    .weight(1f)
                    .hazeSource(sheetHaze),
            )
        }

        // Пилюля вкладок (или подпись к выбранному) прибита к низу экрана и уезжает вместе со шторкой.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, (offsetPx - partialTop).coerceAtLeast(0f).roundToInt()) }
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            CompositionLocalProvider(LocalChatGlassState provides sheetHaze) {
                AnimatedContent(
                    targetState = selection.isEmpty(),
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "attach_bottom_bar",
                ) { isEmpty ->
                    if (isEmpty) {
                        TabBar(
                            canShareLocation = canShareLocation,
                            enableTextFormatting = enableTextFormatting,
                            onAction = onAction,
                        )
                    } else {
                        CaptionBar(
                            caption = caption,
                            onCaptionChange = { caption = it },
                            count = selection.size,
                            onSend = {
                                onAction(TgAttachAction.Send(selection.toList(), caption.trim().ifEmpty { null }, compress = true))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    selection: List<GalleryMedia>,
    isExpanded: Boolean,
    titleHeight: Dp,
    topPadding: Dp,
    onDrag: (Float) -> Float,
    onDragStopped: (Float) -> Unit,
    onClose: () -> Unit,
    onPreviewSelection: () -> Unit,
    onSendUncompressed: () -> Unit,
) {
    val dragState = rememberDraggableState { delta -> onDrag(delta) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> onDragStopped(velocity) },
            )
            .padding(top = topPadding),
    ) {
        if (!isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(ElementTheme.colors.iconQuaternary),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleHeight)
                .clipToBounds(),
            contentAlignment = Alignment.BottomStart,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(TITLE_HEIGHT)
                    .padding(start = if (isExpanded) 4.dp else 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isExpanded) {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = CompoundIcons.Close(), contentDescription = stringResource(CommonStrings.action_close))
                    }
                }
                if (selection.isEmpty()) {
                    Text(
                        text = stringResource(R.string.larpgram_attach_tab_gallery),
                        style = ElementTheme.typography.fontHeadingSmMedium,
                        color = ElementTheme.colors.textPrimary,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onPreviewSelection)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectionTitle(selection),
                            style = ElementTheme.typography.fontHeadingSmMedium,
                            color = ElementTheme.colors.textPrimary,
                        )
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = CompoundIcons.ChevronRight(),
                            contentDescription = null,
                            tint = ElementTheme.colors.iconSecondary,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (selection.isNotEmpty()) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = CompoundIcons.OverflowVertical(),
                                contentDescription = stringResource(CommonStrings.action_open_context_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.larpgram_attach_send_uncompressed)) },
                                onClick = {
                                    showMenu = false
                                    onSendUncompressed()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun selectionTitle(selection: List<GalleryMedia>): String {
    val count = selection.size
    return when {
        selection.all { !it.isVideo } -> pluralStringResource(R.plurals.larpgram_attach_selected_photos, count, count)
        selection.all { it.isVideo } -> pluralStringResource(R.plurals.larpgram_attach_selected_videos, count, count)
        else -> pluralStringResource(R.plurals.larpgram_attach_selected_media, count, count)
    }
}

@Composable
private fun GalleryGrid(
    access: GalleryAccess,
    deniedForever: Boolean,
    media: List<GalleryMedia>?,
    selection: MutableList<GalleryMedia>,
    bottomPadding: Dp,
    onRequestAccess: () -> Unit,
    onAction: (TgAttachAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tileSize = (maxWidth - GRID_PADDING * 2 - GRID_GAP * (COLUMNS - 1)) / COLUMNS
        val thumbSizePx = with(LocalDensity.current) { tileSize.roundToPx() }.coerceAtMost(MAX_THUMB_PX)
        val items = media.orEmpty()
        val firstBlock = items.take(4)
        val rest = items.drop(4)

        fun toggle(item: GalleryMedia) {
            if (!selection.remove(item) && selection.size < TG_ATTACH_MAX_SELECTION) selection.add(item)
        }

        fun onTileClick(item: GalleryMedia) {
            if (selection.isEmpty()) onAction(TgAttachAction.Preview(listOf(item))) else toggle(item)
        }

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Fixed(COLUMNS),
            contentPadding = PaddingValues(start = GRID_PADDING, end = GRID_PADDING, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
            verticalArrangement = Arrangement.spacedBy(GRID_GAP),
        ) {
            if (access == GalleryAccess.Partial) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "partial") {
                    PartialAccessBanner(onManage = onRequestAccess)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "first") {
                Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                    val cameraDescription = stringResource(R.string.larpgram_attach_camera)
                    TgAttachCameraTile(
                        modifier = Modifier
                            .width(tileSize)
                            .height(tileSize * 2 + GRID_GAP)
                            .semantics { contentDescription = cameraDescription },
                        onClick = { onAction(TgAttachAction.CameraPhoto) },
                        onLongClick = { onAction(TgAttachAction.CameraVideo) },
                    )
                    if (access == GalleryAccess.None) {
                        NoAccessPrompt(
                            deniedForever = deniedForever,
                            onRequestAccess = onRequestAccess,
                            onSystemGallery = { onAction(TgAttachAction.SystemGallery) },
                            modifier = Modifier
                                .weight(1f)
                                .height(tileSize * 2 + GRID_GAP),
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                            for (row in 0 until 2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                                    for (column in 0 until 2) {
                                        val item = firstBlock.getOrNull(row * 2 + column)
                                        if (item != null) {
                                            GalleryTile(
                                                media = item,
                                                selectionNumber = selection.indexOf(item) + 1,
                                                thumbSizePx = thumbSizePx,
                                                onClick = { onTileClick(item) },
                                                onToggle = { toggle(item) },
                                                modifier = Modifier.size(tileSize),
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.size(tileSize))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            items(rest, key = { it.id }, contentType = { "media" }) { item ->
                GalleryTile(
                    media = item,
                    selectionNumber = selection.indexOf(item) + 1,
                    thumbSizePx = thumbSizePx,
                    onClick = { onTileClick(item) },
                    onToggle = { toggle(item) },
                    modifier = Modifier.aspectRatio(1f),
                )
            }
        }
    }
}

@Composable
private fun GalleryTile(
    media: GalleryMedia,
    selectionNumber: Int,
    thumbSizePx: Int,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumbnail by produceState(GalleryThumbnails.cached(media), media.id) {
        if (value == null) value = GalleryThumbnails.load(context, media, thumbSizePx)
    }
    val isSelected = selectionNumber > 0
    val scale by animateFloatAsState(if (isSelected) SELECTED_SCALE else 1f, label = "tile_scale")
    val placeholder = if (ElementTheme.isLightTheme) Color(0xFFF0F2F5) else Color(0xFF2A2A2C)
    Box(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(placeholder),
        ) {
            thumbnail?.let {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }
            if (media.isVideo) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x66000000))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(12.dp),
                        imageVector = CompoundIcons.PlaySolid(),
                        contentDescription = null,
                        tint = Color.White,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = formatDuration(media.durationMs),
                        color = Color.White,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
        // Круг выбора в правом верхнем углу, зона нажатия больше самого круга.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
        ) {
            SelectionCircle(
                number = selectionNumber,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp),
            )
        }
    }
}

@Composable
private fun SelectionCircle(number: Int, modifier: Modifier = Modifier) {
    val isSelected = number > 0
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (isSelected) ElementTheme.colors.bgAccentRest else Color(0x33000000))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Text(
                text = number.toString(),
                color = Color.White,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun NoAccessPrompt(
    deniedForever: Boolean,
    onRequestAccess: () -> Unit,
    onSystemGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.larpgram_attach_access_title),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.larpgram_attach_access_text),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            text = stringResource(
                if (deniedForever) R.string.larpgram_attach_access_settings else R.string.larpgram_attach_access_allow
            ),
            size = ButtonSize.Medium,
            onClick = onRequestAccess,
        )
        TextButton(
            text = stringResource(R.string.larpgram_attach_system_gallery),
            size = ButtonSize.Medium,
            onClick = onSystemGallery,
        )
    }
}

@Composable
private fun PartialAccessBanner(onManage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.larpgram_attach_partial_access),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
        TextButton(
            text = stringResource(R.string.larpgram_attach_partial_manage),
            size = ButtonSize.Small,
            onClick = onManage,
        )
    }
}

private data class AttachTab(
    val icon: @Composable () -> ImageVector,
    val label: Int,
    val action: TgAttachAction?,
)

@Composable
private fun TabBar(
    canShareLocation: Boolean,
    enableTextFormatting: Boolean,
    onAction: (TgAttachAction) -> Unit,
) {
    val tabs = buildList {
        add(AttachTab({ CompoundIcons.Image() }, R.string.larpgram_attach_tab_gallery, null))
        add(AttachTab({ CompoundIcons.Document() }, R.string.larpgram_attach_tab_file, TgAttachAction.Files))
        if (canShareLocation) add(AttachTab({ CompoundIcons.LocationPin() }, R.string.larpgram_attach_tab_location, TgAttachAction.Location))
        add(AttachTab({ CompoundIcons.Polls() }, R.string.larpgram_attach_tab_poll, TgAttachAction.Poll))
        if (enableTextFormatting) {
            add(AttachTab({ CompoundIcons.TextFormatting() }, R.string.larpgram_attach_tab_format, TgAttachAction.TextFormatting))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .height(TAB_BAR_HEIGHT)
            .tgGlass(RoundedCornerShape(TAB_BAR_HEIGHT / 2))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.action == null
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(TAB_BAR_HEIGHT / 2))
                    .background(if (isSelected) ElementTheme.colors.bgSubtleSecondary else Color.Transparent)
                    .clickable { tab.action?.let(onAction) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = if (isSelected) ElementTheme.colors.textActionAccent else ElementTheme.colors.iconPrimary
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = tab.icon(),
                    contentDescription = null,
                    tint = tint,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(tab.label),
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = if (isSelected) ElementTheme.colors.textActionAccent else ElementTheme.colors.textPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CaptionBar(
    caption: String,
    onCaptionChange: (String) -> Unit,
    count: Int,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 7.dp, end = 7.dp, top = 6.dp, bottom = 9.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .tgGlass(RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                modifier = Modifier.fillMaxWidth(),
                value = caption,
                onValueChange = onCaptionChange,
                textStyle = ElementTheme.typography.fontBodyLgRegular.copy(color = ElementTheme.colors.textPrimary),
                cursorBrush = SolidColor(ElementTheme.colors.textActionAccent),
                maxLines = 5,
                decorationBox = { inner ->
                    if (caption.isEmpty()) {
                        Text(
                            text = stringResource(R.string.larpgram_attach_caption_hint),
                            style = ElementTheme.typography.fontBodyLgRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    }
                    inner()
                },
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(ElementTheme.colors.bgAccentRest)
                    .clickable(onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = CompoundIcons.SendSolid(),
                    contentDescription = stringResource(CommonStrings.action_send),
                    tint = Color.White,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-6).dp, y = (-6).dp)
                    .heightIn(min = 22.dp)
                    .clip(CircleShape)
                    .background(ElementTheme.colors.bgAccentRest)
                    .border(2.dp, if (ElementTheme.isLightTheme) Color.White else Color(0xFF1C1C1D), CircleShape)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    color = Color.White,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val COLUMNS = 3
private val GRID_GAP = 2.dp
private val GRID_PADDING = 2.dp
private val SHEET_RADIUS = 20.dp
private val TAB_BAR_HEIGHT = 60.dp
private val TITLE_HEIGHT = 52.dp
private const val SELECTED_SCALE = 0.787f
private const val PARTIAL_TOP_FRACTION = 0.42f
private const val SCRIM_ALPHA = 0.4f
private const val FLING_VELOCITY = 1200f
private const val MAX_THUMB_PX = 384
