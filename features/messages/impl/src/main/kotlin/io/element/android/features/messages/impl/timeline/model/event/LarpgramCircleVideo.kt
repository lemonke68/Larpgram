/*
 * Правка форка: кружочки, круглые видеосообщения как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.model.event

/**
 * Префикс имени файла, которым помечается кружочек.
 *
 * Своё поле в content добавить нечем: `Timeline.sendVideo` его не принимает, а отправлять
 * событие вручную нельзя, иначе в шифрованной комнате медиа останется незашифрованным.
 * Имя файла доезжает до клиента в целости, поэтому маркер живёт в нём.
 *
 * Чужие клиенты покажут такое сообщение обычным видео, и это нормально.
 */
const val LARPGRAM_CIRCLE_FILENAME_PREFIX = "larpgram-circle"

/** Кружочек ли это. */
val TimelineItemVideoContent.isLarpgramCircle: Boolean
    get() = filename.startsWith(LARPGRAM_CIRCLE_FILENAME_PREFIX)
