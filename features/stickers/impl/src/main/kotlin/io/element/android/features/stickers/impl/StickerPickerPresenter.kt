/*
 * Модуль форка: пикер стикеров.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.stickers.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackSource
import io.element.android.features.stickers.impl.import.ImportResult
import io.element.android.features.stickers.impl.import.StickerPackImporter
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Inject
@ContributesBinding(RoomScope::class)
class StickerPickerPresenter(
    private val imagePackSource: ImagePackSource,
    private val room: JoinedRoom,
    private val importer: StickerPackImporter,
) : Presenter<StickerPickerState> {
    @Composable
    override fun present(): StickerPickerState {
        val coroutineScope = rememberCoroutineScope()
        var packs by remember { mutableStateOf<ImmutableList<ImagePack>>(persistentListOf()) }
        var selectedPackIndex by remember { mutableIntStateOf(0) }
        var isLoading by remember { mutableStateOf(true) }
        var importState by remember { mutableStateOf<ImportState>(ImportState.Hidden) }

        suspend fun load() {
            isLoading = true
            // Паки без стикеров в пикере не нужны: они могут состоять из одних эмодзи.
            packs = imagePackSource.getAllPacks()
                .filter { it.stickers.isNotEmpty() }
                .toImmutableList()
            // Пак мог исчезнуть, пока пикер был открыт, поэтому вкладку держим в границах.
            selectedPackIndex = selectedPackIndex.coerceIn(0, (packs.size - 1).coerceAtLeast(0))
            isLoading = false
        }

        LaunchedEffect(Unit) {
            load()
        }

        fun handleEvent(event: StickerPickerEvents) {
            when (event) {
                is StickerPickerEvents.SelectPack -> {
                    if (event.index in packs.indices) {
                        selectedPackIndex = event.index
                    }
                }
                is StickerPickerEvents.SendSticker -> coroutineScope.launch {
                    val image = event.image
                    room.sendSticker(
                        url = image.url,
                        body = image.bestDescription,
                        mimeType = image.mimeType,
                        width = image.width,
                        height = image.height,
                        size = image.size,
                    )
                }
                StickerPickerEvents.ShowImport -> importState = ImportState.Asking
                StickerPickerEvents.DismissImport -> importState = ImportState.Hidden
                is StickerPickerEvents.ImportPack -> coroutineScope.launch {
                    importState = ImportState.InProgress
                    importState = when (val result = importer.import(event.packName)) {
                        is ImportResult.Success -> {
                            // Пак уже записан в account data, осталось показать его в пикере.
                            load()
                            ImportState.Done(
                                packName = result.pack.displayName.orEmpty(),
                                skipped = result.skipped,
                            )
                        }
                        ImportResult.NotFound -> ImportState.Error("Такого пака нет. Проверь название.")
                        is ImportResult.NoStaticStickers -> ImportState.Error(
                            "В этом паке только анимированные стикеры, их пока не умеем."
                        )
                        ImportResult.Failed -> ImportState.Error("Не получилось. Попробуй ещё раз.")
                    }
                }
                StickerPickerEvents.Refresh -> coroutineScope.launch { load() }
            }
        }

        return StickerPickerState(
            packs = packs,
            selectedPackIndex = selectedPackIndex,
            isLoading = isLoading,
            importState = importState,
            eventSink = ::handleEvent,
        )
    }
}
