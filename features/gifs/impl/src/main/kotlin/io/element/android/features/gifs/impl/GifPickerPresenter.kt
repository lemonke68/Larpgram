/*
 * Модуль форка: гифки через свой прокси к Tenor.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.gifs.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@Inject
@ContributesBinding(RoomScope::class)
class GifPickerPresenter(
    private val repository: GifRepository,
    private val sender: GifSender,
    private val room: JoinedRoom,
    private val recentGifsStore: RecentGifsStore,
) : Presenter<GifPickerState> {
    @Composable
    override fun present(): GifPickerState {
        val coroutineScope = rememberCoroutineScope()
        var query by remember { mutableStateOf("") }
        var gifs by remember { mutableStateOf<ImmutableList<Gif>>(persistentListOf()) }
        var isLoading by remember { mutableStateOf(true) }
        var hasFailed by remember { mutableStateOf(false) }
        var retryCount by remember { mutableStateOf(0) }
        var isShowingRecent by remember { mutableStateOf(false) }

        LaunchedEffect(query, retryCount) {
            isLoading = true
            hasFailed = false

            // Пустой запрос: сначала показываем свои недавние. Это и привычнее (как
            // сохранённые в Telegram), и не тратит квоту Giphy.
            if (query.isBlank()) {
                val recent = recentGifsStore.getRecent()
                if (recent.isNotEmpty()) {
                    gifs = recent.toImmutableList()
                    isShowingRecent = true
                    isLoading = false
                    return@LaunchedEffect
                }
            }
            isShowingRecent = false
            // Пауза перед запросом: пока человек печатает, дёргать прокси на каждую букву
            // незачем. Для пустого запроса ждать нечего, подборка грузится сразу.
            if (query.isNotBlank()) {
                delay(SEARCH_DEBOUNCE_MS)
            }
            repository.search(query)
                .onSuccess {
                    gifs = it.gifs.toImmutableList()
                    hasFailed = false
                }
                .onFailure {
                    gifs = persistentListOf()
                    hasFailed = true
                }
            isLoading = false
        }

        fun handleEvent(event: GifPickerEvents) {
            when (event) {
                is GifPickerEvents.QueryChanged -> query = event.query
                is GifPickerEvents.SendGif -> coroutineScope.launch {
                    // Запоминаем сразу: даже если отправка не долетит, гифка человеку нужна.
                    recentGifsStore.remember(event.gif)
                    // Список недавних поменялся, иначе при следующем открытии он был бы старым.
                    if (query.isBlank()) {
                        gifs = recentGifsStore.getRecent().toImmutableList()
                        isShowingRecent = gifs.isNotEmpty()
                    }
                    sender.send(room, event.gif)
                        // Молча терять отправку нельзя: без лога такая поломка выглядит как
                        // «нажал, и ничего не произошло».
                        .onFailure { Timber.e(it, "не удалось отправить гифку") }
                }
                GifPickerEvents.Retry -> retryCount++
            }
        }

        return GifPickerState(
            query = query,
            gifs = gifs,
            isLoading = isLoading,
            hasFailed = hasFailed,
            isShowingRecent = isShowingRecent,
            eventSink = ::handleEvent,
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
