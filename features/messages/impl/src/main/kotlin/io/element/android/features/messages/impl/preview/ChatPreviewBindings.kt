/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.preview

import dev.zacsweers.metro.ContributesTo
import io.element.android.features.messages.impl.timeline.di.TimelineItemPresenterFactories
import io.element.android.libraries.di.RoomScope

/** Что превью чата берёт из графа комнаты, созданного вне навигации (см. [DefaultChatPreviewRenderer]). */
@ContributesTo(RoomScope::class)
interface ChatPreviewBindings {
    val chatPreviewPresenter: ChatPreviewPresenter
    val timelineItemPresenterFactories: TimelineItemPresenterFactories
}
