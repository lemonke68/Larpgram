/*
 * Модуль форка: гифки через свой прокси к Tenor.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.gifs.impl

import kotlinx.collections.immutable.ImmutableList

/**
 * @param query что набрано в поиске, пусто означает подборку популярного.
 * @param gifs текущая выдача.
 * @param isLoading идёт первый запрос по текущему запросу.
 * @param hasFailed запрос не удался: нет сети, прокси лежит или ключ не задан.
 */
data class GifPickerState(
    val query: String,
    val gifs: ImmutableList<Gif>,
    val isLoading: Boolean,
    val hasFailed: Boolean,
    /** Показываются недавно отправленные, а не выдача поиска. */
    val isShowingRecent: Boolean,
    val eventSink: (GifPickerEvents) -> Unit,
) {
    /** Показывать «ничего не нашлось»: искали, не упали, а результата нет. */
    val isEmpty: Boolean get() = !isLoading && !hasFailed && gifs.isEmpty()
}

sealed interface GifPickerEvents {
    /** Пользователь меняет текст поиска. */
    data class QueryChanged(val query: String) : GifPickerEvents

    /** Пользователь выбрал гифку: отправляем её в комнату. */
    data class SendGif(val gif: Gif) : GifPickerEvents

    /** Повторить неудавшийся запрос. */
    data object Retry : GifPickerEvents
}
