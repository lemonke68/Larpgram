/*
 * Правка форка: вкладка «Ссылки» в профиле чата.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.mediaviewer.impl.profile

import android.util.Patterns
import androidx.compose.runtime.Immutable
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.room.CreateTimelineParams
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.api.timeline.item.virtual.VirtualTimelineItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale

/** Сообщение со ссылками, строка вкладки «Ссылки» (`SharedLinkCell` TG). */
@Immutable
data class ProfileLinkItem(
    val id: UniqueId,
    val eventId: EventId?,
    /** Имя сайта: «Mango-kokos» для larpgram.mango-kokos.ru, как у TG без превью страницы. */
    val title: String,
    /** Текст сообщения, если в нём есть что-то кроме самой ссылки. */
    val text: String?,
    val urls: ImmutableList<String>,
    val dateSent: String,
)

@Immutable
data class ProfileLinksState(
    val items: ImmutableList<ProfileLinkItem>,
    /** Время последнего «грузим ещё» от ленты, null — история загружена целиком. */
    val loaderTimestamp: Long?,
    val isFailed: Boolean,
)

/**
 * Отдельная лента комнаты только из текстовых сообщений (`CreateTimelineParams.TextOnly`), из
 * которой вынимаются ссылки. Лента живёт, пока жив [start]-scope.
 */
class ProfileLinksDataSource(
    private val room: JoinedRoom,
    private val dateFormatter: DateFormatter,
    private val dispatchers: CoroutineDispatchers,
) {
    private var timeline: Timeline? = null
    private val _state = MutableStateFlow(ProfileLinksState(persistentListOf(), loaderTimestamp = 0L, isFailed = false))
    val state: StateFlow<ProfileLinksState> = _state

    fun start(scope: CoroutineScope) {
        scope.launch {
            room.createTimeline(CreateTimelineParams.TextOnly)
                .onSuccess { newTimeline ->
                    timeline = newTimeline
                    try {
                        newTimeline.timelineItems.collect { items ->
                            _state.value = withContext(dispatchers.computation) { map(items) }
                        }
                    } finally {
                        timeline = null
                        newTimeline.close()
                    }
                }
                .onFailure {
                    Timber.e(it, "Не получилось открыть ленту ссылок ${room.roomId}")
                    _state.value = ProfileLinksState(persistentListOf(), loaderTimestamp = null, isFailed = true)
                }
        }
    }

    suspend fun loadMore() {
        timeline?.paginate(Timeline.PaginationDirection.BACKWARDS)
    }

    private fun map(items: List<MatrixTimelineItem>): ProfileLinksState {
        var loaderTimestamp: Long? = null
        val links = buildList {
            // Лента идёт от старых к новым, вкладка — от новых к старым.
            for (item in items.asReversed()) {
                when (item) {
                    is MatrixTimelineItem.Event -> {
                        val type = (item.event.content as? MessageContent)?.type as? TextMessageType ?: continue
                        val urls = extractUrls(type.body)
                        if (urls.isEmpty()) continue
                        add(
                            ProfileLinkItem(
                                id = item.uniqueId,
                                eventId = item.eventId,
                                title = siteName(urls.first()),
                                text = textWithoutUrls(type.body),
                                urls = urls.toImmutableList(),
                                dateSent = dateFormatter.format(item.event.timestamp, mode = DateFormatterMode.Day),
                            )
                        )
                    }
                    is MatrixTimelineItem.Virtual -> {
                        val virtual = item.virtual
                        if (virtual is VirtualTimelineItem.LoadingIndicator && virtual.direction == Timeline.PaginationDirection.BACKWARDS) {
                            loaderTimestamp = virtual.timestamp
                        }
                    }
                    MatrixTimelineItem.Other -> Unit
                }
            }
        }
        return ProfileLinksState(links.toImmutableList(), loaderTimestamp = loaderTimestamp, isFailed = false)
    }

    companion object {
        /** Ссылки из текста; схему дописываем, как это делает Linkify. */
        fun extractUrls(text: String): List<String> {
            val matcher = Patterns.WEB_URL.matcher(text)
            val result = LinkedHashSet<String>()
            while (matcher.find()) {
                val raw = matcher.group()
                // Почту («a@b.ru») и слова без домена верхнего уровня пропускаем.
                val start = matcher.start()
                if (start > 0 && text[start - 1] == '@') continue
                val url = if (raw.contains("://")) raw else "https://$raw"
                if (hostOf(url)?.contains('.') != true) continue
                result.add(url)
            }
            return result.toList()
        }

        /** Текст сообщения без самих ссылок (они и так строками ниже); null, если кроме ссылок ничего нет. */
        fun textWithoutUrls(text: String): String? {
            return Patterns.WEB_URL.matcher(text).replaceAll("")
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
                .takeIf { it.isNotBlank() }
        }

        /** «larpgram.mango-kokos.ru» → «Mango-kokos», «t.me» → «T». */
        fun siteName(url: String): String {
            val host = hostOf(url)?.removePrefix("www.") ?: return url
            val labels = host.split('.').filter { it.isNotEmpty() }
            val name = if (labels.size >= 2) labels[labels.size - 2] else labels.firstOrNull() ?: host
            return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        private fun hostOf(url: String): String? {
            val afterScheme = url.substringAfter("://", url)
            return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
                .substringAfter('@')
                .takeIf { it.isNotEmpty() }
        }
    }
}
