/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.linkpreview

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import io.element.android.features.messages.impl.timeline.di.TimelineItemEventContentKey
import io.element.android.features.messages.impl.timeline.di.TimelineItemPresenterFactory
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.libraries.di.RoomScope

@BindingContainer
@ContributesTo(RoomScope::class)
interface LinkPreviewPresenterBindingContainer {
    @Binds
    @IntoMap
    @TimelineItemEventContentKey(TimelineItemTextContent::class)
    fun bindLinkPreviewPresenterFactory(factory: LinkPreviewPresenter.Factory): TimelineItemPresenterFactory<*, *>
}
