/*
 * Модуль форка: пикер стикеров.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.stickers.impl

import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackImage
import kotlinx.collections.immutable.ImmutableList

/**
 * @param packs паки, в которых есть хотя бы один стикер.
 * @param selectedPackIndex какая вкладка открыта, всегда в границах [packs].
 * @param isLoading true пока паки грузятся в первый раз.
 */
data class StickerPickerState(
    val packs: ImmutableList<ImagePack>,
    val selectedPackIndex: Int,
    val isLoading: Boolean,
    val importState: ImportState,
    val eventSink: (StickerPickerEvents) -> Unit,
) {
    /** Стикеры выбранного пака, пустой список если паков нет. */
    val visibleStickers: List<ImagePackImage>
        get() = packs.getOrNull(selectedPackIndex)?.stickers.orEmpty()

    /** Показывать ли «стикеров нет»: грузить уже закончили, а показывать нечего. */
    val isEmpty: Boolean get() = !isLoading && packs.isEmpty()
}

/** Состояние окна «добавить пак». */
sealed interface ImportState {
    /** Окно закрыто. */
    data object Hidden : ImportState

    /** Окно открыто, ждём ввода. */
    data object Asking : ImportState

    /** Идёт импорт: качаем стикеры и заливаем к себе. */
    data object InProgress : ImportState

    /** Готово. */
    data class Done(val packName: String, val skipped: Int) : ImportState

    /** Не получилось, с человеческой причиной. */
    data class Error(val message: String) : ImportState
}

sealed interface StickerPickerEvents {
    /** Открыть окно добавления пака. */
    data object ShowImport : StickerPickerEvents

    /** Закрыть окно добавления. */
    data object DismissImport : StickerPickerEvents

    /** Импортировать пак по имени или ссылке t.me/addstickers/... */
    data class ImportPack(val packName: String) : StickerPickerEvents

    /** Пользователь переключил вкладку пака. */
    data class SelectPack(val index: Int) : StickerPickerEvents

    /** Пользователь выбрал стикер: отправляем его в текущую комнату. */
    data class SendSticker(val image: ImagePackImage) : StickerPickerEvents

    /** Перечитать паки: например, после импорта нового. */
    data object Refresh : StickerPickerEvents
}
