/*
 * Правка форка: вкладки общих медиа в профиле чата, как `SharedMediaLayout` в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.mediaviewer.api

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Встраиваемые вкладки «Медиа / Файлы / Ссылки / Голосовые» для экрана профиля комнаты.
 * Живёт в RoomScope: сам заводит отдельные ленты комнаты (медиа и текст со ссылками) и
 * закрывает их, когда профиль уходит с экрана.
 */
interface ProfileSharedMedia {
    /**
     * @param onOpenMedia тап по фото, видео или файлу — открыть просмотрщик с этими параметрами.
     */
    @Composable
    fun rememberSection(onOpenMedia: (MediaViewerEntryPoint.Params) -> Unit): ProfileSharedMediaSection
}

enum class ProfileSharedMediaTab {
    Media,
    Files,
    Links,
    Voice,
}

@Stable
interface ProfileSharedMediaSection {
    /** Кладёт содержимое вкладки [tab] в LazyColumn профиля (ниже полосы вкладок). */
    fun content(scope: LazyListScope, tab: ProfileSharedMediaTab)
}

/** Заглушка для превью и тестов: вкладки пустые. */
object NoOpProfileSharedMediaSection : ProfileSharedMediaSection {
    override fun content(scope: LazyListScope, tab: ProfileSharedMediaTab) = Unit
}
