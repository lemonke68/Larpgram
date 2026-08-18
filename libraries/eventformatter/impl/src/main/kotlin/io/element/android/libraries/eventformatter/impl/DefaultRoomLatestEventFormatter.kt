/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.eventformatter.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.DEFAULT_SAFE_LENGTH
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.eventformatter.api.RoomLatestEventFormatter
import io.element.android.libraries.eventformatter.impl.mode.RenderingMode
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkParser
import io.element.android.libraries.matrix.api.roomlist.LatestEventValue
import io.element.android.libraries.matrix.api.timeline.item.event.AudioMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.CallNotifyContent
import io.element.android.libraries.matrix.api.timeline.item.event.EmoteMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.api.timeline.item.event.FailedToParseMessageLikeContent
import io.element.android.libraries.matrix.api.timeline.item.event.FailedToParseStateContent
import io.element.android.libraries.matrix.api.timeline.item.event.FileMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.GalleryMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.ImageMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.LegacyCallInviteContent
import io.element.android.libraries.matrix.api.timeline.item.event.LiveLocationContent
import io.element.android.libraries.matrix.api.timeline.item.event.LocationMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.MessageType
import io.element.android.libraries.matrix.api.timeline.item.event.MessageTypeWithAttachment
import io.element.android.libraries.matrix.api.timeline.item.event.NoticeMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.OtherMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.PollContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileChangeContent
import io.element.android.libraries.matrix.api.timeline.item.event.RedactedContent
import io.element.android.libraries.matrix.api.timeline.item.event.RoomMembershipContent
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.libraries.matrix.api.timeline.item.event.StickerContent
import io.element.android.libraries.matrix.api.timeline.item.event.StickerMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnknownContent
import io.element.android.libraries.matrix.api.timeline.item.event.VideoMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.VoiceMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import io.element.android.libraries.matrix.api.timeline.item.event.isGif
import io.element.android.libraries.matrix.api.timeline.item.event.isLarpgramCircle
import io.element.android.libraries.matrix.api.timeline.item.event.larpgramPreviewThumbnail
import io.element.android.libraries.matrix.api.timeline.item.event.larpgramStickerEmojiOrNull
import io.element.android.libraries.matrix.ui.messages.toPlainText
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.toolbox.api.strings.StringProvider

@ContributesBinding(SessionScope::class)
class DefaultRoomLatestEventFormatter(
    private val sp: StringProvider,
    private val roomMembershipContentFormatter: RoomMembershipContentFormatter,
    private val profileChangeContentFormatter: ProfileChangeContentFormatter,
    private val stateContentFormatter: StateContentFormatter,
    private val rtcNotificationContentFormatter: RtcNotificationContentFormatter,
    private val permalinkParser: PermalinkParser,
) : RoomLatestEventFormatter {
    override fun format(
        latestEvent: LatestEventValue.Local,
        isDmRoom: Boolean,
    ): CharSequence? = formatContent(
        content = latestEvent.content,
        isDmRoom = isDmRoom,
        isOutgoing = true,
        senderId = latestEvent.senderId,
        senderDisambiguatedDisplayName = latestEvent.senderProfile.getDisambiguatedDisplayName(latestEvent.senderId)
    )

    override fun format(
        latestEvent: LatestEventValue.Remote,
        isDmRoom: Boolean,
    ): CharSequence? = formatContent(
        content = latestEvent.content,
        isDmRoom = isDmRoom,
        isOutgoing = latestEvent.isOwn,
        senderId = latestEvent.senderId,
        senderDisambiguatedDisplayName = latestEvent.senderProfile.getDisambiguatedDisplayName(latestEvent.senderId)
    )

    private fun formatContent(
        content: EventContent,
        isDmRoom: Boolean,
        isOutgoing: Boolean,
        senderId: UserId,
        senderDisambiguatedDisplayName: String
    ): CharSequence? {
        return when (content) {
            is MessageContent -> content.process(senderDisambiguatedDisplayName, isDmRoom, isOutgoing)
            RedactedContent -> {
                val message = sp.getString(CommonStrings.common_message_removed)
                message.prefixIfNeeded(senderDisambiguatedDisplayName, isDmRoom, isOutgoing)
            }
            is StickerContent -> {
                stickerSummary(content.bestDescription)
                    .withThumbnailSlotIfAny(content)
                    .prefixIfNeeded(senderDisambiguatedDisplayName, isDmRoom, isOutgoing)
            }
            is UnableToDecryptContent -> {
                val message = sp.getString(CommonStrings.common_waiting_for_decryption_key)
                message.prefixIfNeeded(senderDisambiguatedDisplayName, isDmRoom, isOutgoing)
            }
            is RoomMembershipContent -> {
                roomMembershipContentFormatter.format(content, senderDisambiguatedDisplayName, isOutgoing)
            }
            is ProfileChangeContent -> {
                profileChangeContentFormatter.format(content, senderId, senderDisambiguatedDisplayName, isOutgoing)
            }
            is StateContent -> {
                stateContentFormatter.format(content, senderDisambiguatedDisplayName, isOutgoing, RenderingMode.RoomList)
            }
            is PollContent -> {
                content.question.prefixWith(sp.getString(CommonStrings.common_poll_summary_prefix))
                    .prefixIfNeeded(senderDisambiguatedDisplayName, isDmRoom, isOutgoing)
            }
            is FailedToParseMessageLikeContent, is FailedToParseStateContent, is UnknownContent -> {
                val message = sp.getString(CommonStrings.common_unsupported_event)
                message.prefixIfNeeded(senderDisambiguatedDisplayName, isDmRoom, isOutgoing)
            }
            is LiveLocationContent -> {
                val message = sp.getString(CommonStrings.common_shared_live_location)
                message.prefixIfNeeded(senderDisambiguatedDisplayName, isDmRoom, isOutgoing)
            }
            is LegacyCallInviteContent -> sp.getString(CommonStrings.common_unsupported_call)
            is CallNotifyContent -> rtcNotificationContentFormatter.format(content, isDmRoom)
        }?.take(DEFAULT_SAFE_LENGTH)
    }

    private fun MessageContent.process(
        senderDisambiguatedDisplayName: String,
        isDmRoom: Boolean,
        isOutgoing: Boolean
    ): CharSequence {
        val content = this
        val message = when (val messageType: MessageType = type) {
            // Doesn't need a prefix
            is EmoteMessageType -> {
                return "* $senderDisambiguatedDisplayName ${messageType.body}"
            }
            is TextMessageType -> {
                messageType.toPlainText(permalinkParser)
            }
            is VideoMessageType -> {
                messageType.captionOr(
                    if (messageType.isLarpgramCircle) {
                        sp.getString(CommonStrings.larpgram_video_message)
                    } else {
                        sp.getString(CommonStrings.common_video)
                    }
                )
            }
            is ImageMessageType -> {
                messageType.captionOr(
                    if (messageType.isGif) {
                        sp.getString(CommonStrings.common_gif)
                    } else {
                        sp.getString(CommonStrings.common_image)
                    }
                )
            }
            is StickerMessageType -> {
                messageType.captionOr(stickerSummary(messageType.filename))
            }
            is LocationMessageType -> {
                sp.getString(CommonStrings.common_shared_location)
            }
            is FileMessageType -> {
                // У документа Telegram показывает имя файла, а не слово «Файл».
                messageType.captionOr(messageType.filename)
            }
            is AudioMessageType -> {
                messageType.captionOr(messageType.filename.ifBlank { sp.getString(CommonStrings.common_audio) })
            }
            is VoiceMessageType -> {
                messageType
                    .toPlainText(permalinkParser, "")
                    .takeIf { it.isNotEmpty() }
                    ?.prefixWith(sp.getString(CommonStrings.common_voice_message))
                    ?: sp.getString(CommonStrings.common_voice_message)
            }
            is OtherMessageType -> {
                messageType.body
            }
            is GalleryMessageType -> {
                messageType.body.ifBlank { sp.getString(CommonStrings.common_gallery) }
            }
            is NoticeMessageType -> {
                messageType.body
            }
        }
        return message
            .withThumbnailSlotIfAny(content)
            .prefixIfNeeded(senderDisambiguatedDisplayName, isDmRoom, isOutgoing)
    }

    /**
     * Правка форка: тип сообщения это слово, а не префикс через двоеточие.
     *
     * В Telegram тип показывается иконкой, а словом становится только когда текста нет:
     * «Фото» без подписи, но сама подпись, если она есть. У апстрима тип вешался вторым
     * префиксом поверх имени отправителя, и получалась цепочка вида `Вы: Стикер: 😂`.
     *
     * Имя файла наружу не пускаем никогда: у кружочка оно служебное
     * (`larpgram-circle-b73db9d6…`), и в списке чатов выглядело поломкой.
     */
    private fun MessageTypeWithAttachment.captionOr(typeWord: String): CharSequence =
        toPlainText(permalinkParser, default = "")
            .takeIf { it.isNotBlank() }
            ?: typeWord

    /**
     * Оставляет в тексте место под мини-превью, если у события есть картинка.
     *
     * Условие ровно то же, что у списка чатов (`larpgramPreviewThumbnail`), и это важно:
     * разъедутся — в строке появится пустая дырка либо картинка не влезет вовсе.
     */
    private fun CharSequence.withThumbnailSlotIfAny(content: EventContent): CharSequence =
        if (content.larpgramPreviewThumbnail != null) withThumbnailSlot() else this

    /** «😂 Стикер», как в Telegram; без эмодзи в описании — просто «Стикер». */
    private fun stickerSummary(description: String): String {
        val word = sp.getString(CommonStrings.common_sticker)
        val emoji = description.larpgramStickerEmojiOrNull()
        return if (emoji != null) "$emoji $word" else word
    }

    private fun CharSequence.prefixIfNeeded(
        senderDisambiguatedDisplayName: String,
        isDmRoom: Boolean,
        isOutgoing: Boolean,
    ): CharSequence = if (isDmRoom) {
        this
    } else {
        prefixWith(
            if (isOutgoing) {
                sp.getString(CommonStrings.common_you)
            } else {
                senderDisambiguatedDisplayName
            }
        )
    }
}
