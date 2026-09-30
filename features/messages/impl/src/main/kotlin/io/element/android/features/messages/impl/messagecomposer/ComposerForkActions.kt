/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: что поле ввода Telegram делает сверх элементовского — отправка из меню вложений без
 * экрана предпросмотра, зеркало текстового поста канала в обсуждение, «Черновик:» в списке чатов.
 * Вынесено из `MessageComposerPresenter` (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import dev.zacsweers.metro.Inject
import io.element.android.features.messages.impl.attachments.tgattach.GalleryMedia
import io.element.android.features.messages.impl.attachments.tgattach.UncompressedMediaConfig
import io.element.android.features.messages.impl.attachments.tgattach.sendGalleryMediaNow
import io.element.android.libraries.channelcomments.ChannelDiscussion
import io.element.android.libraries.channelcomments.ChannelPostMirror
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.draft.ComposerDraft
import io.element.android.libraries.matrix.api.room.draft.ComposerDraftType
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.ui.drafts.DraftPreviews
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaSenderFactory
import io.element.android.libraries.textcomposer.model.MessageComposerMode

@Inject
class ComposerForkActions(
    private val room: JoinedRoom,
    private val matrixClient: MatrixClient,
    private val mediaSenderFactory: MediaSenderFactory,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val draftPreviews: DraftPreviews,
) {
    /** Отправка из меню вложений Telegram сразу, без экрана предпросмотра. */
    suspend fun sendGalleryMedia(
        media: List<GalleryMedia>,
        caption: String?,
        compress: Boolean,
        inReplyToEventId: EventId?,
        timelineMode: Timeline.Mode,
    ) = sendGalleryMediaNow(
        media = media,
        caption = caption,
        inReplyToEventId = inReplyToEventId,
        mediaOptimizationConfig = if (compress) mediaOptimizationConfigProvider.get() else UncompressedMediaConfig,
        mediaSender = mediaSenderFactory.create(timelineMode = timelineMode),
        room = room,
        matrixClient = matrixClient,
        snackbarDispatcher = snackbarDispatcher,
    )

    /**
     * Текстовый пост канала с обсуждением уходит обычной отправкой (очередь, локальное эхо, повтор
     * при обрыве сети), а потом зеркалится в обсуждение с id поста, как медиа-посты
     * ([ChannelPostMirror]). Зовётся до отправки; возвращённое зеркалирование — после неё. null —
     * зеркалить нечего (не новое сообщение, не канал или у канала нет обсуждения).
     */
    suspend fun prepareChannelPostMirror(mode: MessageComposerMode): (suspend () -> Unit)? {
        if (mode !is MessageComposerMode.Normal) return null
        val isChannel = (room.info().roomPowerLevels?.values?.eventsDefault ?: 0L) > 0L
        if (!isChannel || ChannelDiscussion.resolveDiscussionRoomId(room, matrixClient) == null) return null
        val preIds = ChannelPostMirror.myPostIds(room, ChannelPostMirror::isTextMessage)
        return {
            runCatchingExceptions {
                ChannelPostMirror.mirrorLastPost(room, matrixClient, preIds, matches = ChannelPostMirror::isTextMessage)
            }
        }
    }

    /**
     * «Черновик:» в строке чата. В TG — только для нового сообщения или ответа в самом чате:
     * незаконченная правка старого сообщения черновиком не считается, треды тоже.
     */
    fun onDraftSaved(draft: ComposerDraft?, isThread: Boolean) {
        if (isThread) return
        draftPreviews.set(room.roomId, draft?.takeIf { it.draftType !is ComposerDraftType.Edit }?.plainText)
    }
}
