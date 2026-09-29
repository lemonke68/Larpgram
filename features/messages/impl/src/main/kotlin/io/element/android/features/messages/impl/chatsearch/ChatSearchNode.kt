/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatsearch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.EventId

@ContributesNode(RoomScope::class)
@AssistedInject
class ChatSearchNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: ChatSearchPresenter,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun viewInTimeline(eventId: EventId)
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        ChatSearchView(
            state = presenter.present(),
            onBackClick = ::navigateUp,
            onResultClick = callback::viewInTimeline,
            modifier = modifier,
        )
    }
}
