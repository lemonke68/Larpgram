/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.tgattach

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.RoomScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Что было в меню вложений перед переходом на экран предпросмотра. */
@Immutable
data class TgAttachRestore(
    val selection: List<GalleryMedia>,
    val caption: String,
    val isExpanded: Boolean,
    val highQuality: Boolean = false,
)

/**
 * Правка форка: меню вложений, из которого ушли на экран предпросмотра Element. В Telegram
 * «Назад» с предпросмотра возвращает в галерею с тем же выбором, а не в чат.
 *
 * Живёт в комнате, а не в экране: пока открыт предпросмотр, экран чата уходит из композиции,
 * и всё, что в нём `remember`, пропадает.
 */
@SingleIn(RoomScope::class)
@Inject
class TgAttachSheetMemory {
    private var waiting: TgAttachRestore? = null

    /** Черновик чата, скопированный в подпись; null при редактировании старого сообщения. */
    var composerDraft: String? = null
        private set
    var sentComposerDraft: String? = null
        private set

    fun onSheetOpened(draft: String?) {
        composerDraft = draft
        sentComposerDraft = null
    }
    private val restoreFlow = MutableStateFlow<TgAttachRestore?>(null)
    private val resetComposerModeFlow = MutableStateFlow(false)

    /** Меню, которое надо открыть снова; null — ничего. */
    val restore: StateFlow<TgAttachRestore?> = restoreFlow.asStateFlow()

    /** Предпросмотр отправил медиа: ответ, начатый до меню, использован. */
    val resetComposerMode: StateFlow<Boolean> = resetComposerModeFlow.asStateFlow()

    fun onOpenPreview(restore: TgAttachRestore) {
        waiting = restore
        restoreFlow.value = null
    }

    /** Подпись, введённая в шторке до открытия предпросмотра. */
    val previewCaption: String? get() = waiting?.caption
    val previewHighQuality: Boolean get() = waiting?.highQuality == true

    /** «Назад» сохраняет изменения подписи и возвращает прежний выбор вложений. */
    fun onPreviewCancelled(caption: String? = null, highQuality: Boolean? = null) {
        restoreFlow.value = waiting?.let { restore ->
            restore.copy(caption = caption ?: restore.caption, highQuality = highQuality ?: restore.highQuality)
        }
        waiting = null
    }

    fun onPreviewSent() {
        if (waiting != null) {
            sentComposerDraft = composerDraft
            resetComposerModeFlow.value = true
        }
        waiting = null
    }

    fun onRestored() {
        restoreFlow.value = null
    }

    fun onComposerModeReset() {
        resetComposerModeFlow.value = false
        sentComposerDraft = null
    }
}
