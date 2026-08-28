/*
 * Правка форка: по тапу на стикер в чате показываем его стикер-пак.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackEventTypes
import io.element.android.libraries.imagepacks.api.ImagePackId
import io.element.android.libraries.imagepacks.api.ImagePackImage
import io.element.android.libraries.imagepacks.api.ImagePackSource
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Открыть лист стикер-пака для стикера из таймлайна. Кладётся в [io.element.android.features.messages.impl.MessagesView],
 * дёргается по тапу на стикер (`originalJson` события + mxc-ссылка стикера).
 */
val LocalOpenStickerPack = staticCompositionLocalOf<(originalJson: String?, stickerUrl: String?) -> Unit> {
    { _, _ -> }
}

private val descriptorJson = Json { ignoreUnknownKeys = true }

/** Достаёт JSON дескриптора пака из поля `ru.mangokokos.larpgram.pack` в контенте события. */
private fun packDescriptorFromEvent(originalJson: String?): String? {
    originalJson ?: return null
    return runCatching {
        descriptorJson.parseToJsonElement(originalJson).jsonObject["content"]?.jsonObject
            ?.get(ImagePackEventTypes.STICKER_PACK_FIELD)?.jsonObject?.toString()
    }.getOrNull()
}

/**
 * Лист стикер-пака: аватар, название, сетка стикеров и кнопка «Добавить/Удалить (N)».
 *
 * Пак берём из вшитого в событие дескриптора; если его нет (старый/чужой стикер) — пытаемся
 * найти пак по mxc-ссылке среди паков пользователя. Не нашли — листа нет.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StickerPackSheet(
    originalJson: String?,
    stickerUrl: String?,
    imagePackSource: ImagePackSource,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var pack by remember { mutableStateOf<ImagePack?>(null) }
    var isSaved by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refreshSavedState(slug: String) {
        isSaved = imagePackSource.getSavedPacks().any { (it.id as? ImagePackId.Saved)?.slug == slug }
    }

    LaunchedEffect(originalJson, stickerUrl) {
        loading = true
        // 1) вшитый дескриптор пака.
        val fromDescriptor = packDescriptorFromEvent(originalJson)?.let { imagePackSource.parsePackDescriptor(it) }
        // 2) фолбэк: пак пользователя, содержащий этот стикер по mxc-ссылке.
        val resolved = fromDescriptor ?: stickerUrl?.let { url ->
            imagePackSource.getAllPacks().firstOrNull { p -> p.stickers.any { it.url == url } }
        }
        pack = resolved
        (resolved?.id as? ImagePackId.Saved)?.slug?.let { refreshSavedState(it) }
        loading = false
    }

    // Открываем сразу на полную высоту (без промежуточного half-состояния), иначе лист
    // съезжает вниз и кнопка «Добавить/Удалить (N)» уходит за нижний край.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, scrollable = false, sheetState = sheetState) {
        val currentPack = pack
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                currentPack == null -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "Пак этого стикера не найден", color = ElementTheme.colors.textSecondary)
                }
                else -> StickerPackContent(
                    pack = currentPack,
                    isSaved = isSaved,
                    onToggle = {
                        val slug = (currentPack.id as? ImagePackId.Saved)?.slug ?: return@StickerPackContent
                        coroutineScope.launch {
                            if (isSaved) imagePackSource.removeSavedPack(slug) else imagePackSource.savePack(currentPack)
                            refreshSavedState(slug)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StickerPackContent(
    pack: ImagePack,
    isSaved: Boolean,
    onToggle: () -> Unit,
) {
    val name = pack.displayName?.takeIf { it.isNotBlank() } ?: "Стикерпак"
    Text(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        text = name,
        style = ElementTheme.typography.fontHeadingSmMedium,
        color = ElementTheme.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(pack.stickers) { sticker -> StickerCell(sticker) }
    }

    Button(
        text = if (isSaved) {
            "Удалить (${pack.stickers.size}) стикеров"
        } else {
            "Добавить (${pack.stickers.size}) стикеров"
        },
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun StickerCell(sticker: ImagePackImage) {
    Box(
        modifier = Modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxWidth(),
            model = MediaRequestData(
                source = MediaSource(url = sticker.url),
                kind = MediaRequestData.Kind.Thumbnail(
                    width = STICKER_THUMBNAIL_PX,
                    height = STICKER_THUMBNAIL_PX,
                ),
            ),
            contentScale = ContentScale.Fit,
            contentDescription = sticker.bestDescription,
        )
    }
}

private const val STICKER_THUMBNAIL_PX = 128L
