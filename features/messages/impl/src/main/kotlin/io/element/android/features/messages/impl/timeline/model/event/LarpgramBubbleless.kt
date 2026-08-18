/*
 * Правка форка: что показывается без пузыря, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.model.event

import androidx.compose.runtime.Composable
import io.element.android.features.messages.impl.utils.containsOnlyEmojis

/**
 * Стикеры, гифки и кружочки Telegram рисует прямо на обоях, без подложки.
 *
 * Гифка с подписью — исключение: подпись без пузыря повисает на обоях и не читается,
 * поэтому такая гифка остаётся в пузыре, ровно как в Telegram.
 */
val TimelineItemEventContent.isBubbleless: Boolean
    @Composable get() = when (this) {
        is TimelineItemStickerContent -> true
        is TimelineItemImageContent -> mimeType == "image/gif" && !showCaption
        is TimelineItemVideoContent -> isLarpgramCircle
        is TimelineItemTextBasedContent -> isEmojiOnly
        else -> false
    }

/**
 * Сообщение из одних эмодзи: в Telegram оно рисуется крупно и без пузыря.
 *
 * Проверка та же, что уже была в апстриме у `TimelineItemTextView`, где ею только увеличивали
 * шрифт. Вынесена сюда, чтобы по ней же решался пузырь: два разных условия для одного и того
 * же случая рано или поздно разъедутся.
 *
 * Форматированное тело должно совпадать с обычным: если человек написал эмодзи жирным или со
 * ссылкой, это уже оформленный текст, и пузырь ему нужен.
 *
 * Проверка `@Composable` не по своей воле: у апстрима `containsOnlyEmojis` подменяет ответ в
 * превью, потому что за пределами настоящего устройства эмодзи распознаются не всегда.
 */
val TimelineItemTextBasedContent.isEmojiOnly: Boolean
    @Composable get() = formattedBody.toString() == body && body.replace(" ", "").containsOnlyEmojis()
