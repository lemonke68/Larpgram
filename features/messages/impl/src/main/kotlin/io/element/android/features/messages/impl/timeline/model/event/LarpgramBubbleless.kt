/*
 * Правка форка: что показывается без пузыря, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.model.event

/**
 * Стикеры, гифки и кружочки Telegram рисует прямо на обоях, без подложки.
 *
 * Гифка с подписью — исключение: подпись без пузыря повисает на обоях и не читается,
 * поэтому такая гифка остаётся в пузыре, ровно как в Telegram.
 */
val TimelineItemEventContent.isBubbleless: Boolean
    get() = when (this) {
        is TimelineItemStickerContent -> true
        is TimelineItemImageContent -> mimeType == "image/gif" && !showCaption
        is TimelineItemVideoContent -> isLarpgramCircle
        else -> false
    }
