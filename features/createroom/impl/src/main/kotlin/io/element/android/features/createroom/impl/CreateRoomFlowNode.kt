/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.createroom.impl

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.push
import com.bumble.appyx.navmodel.backstack.operation.replace
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.createroom.api.CreateRoomEntryPoint
import io.element.android.features.createroom.impl.addpeople.AddPeopleNode
import io.element.android.features.createroom.impl.configureroom.ConfigureRoomNode
import io.element.android.features.createroom.impl.selectmembers.SelectMembersNode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class CreateRoomFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : BaseFlowNode<CreateRoomFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = initialElementFromInputs(plugins.filterIsInstance<Inputs>().first()),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins
) {
    @Parcelize
    data class Inputs(
        val isSpace: Boolean,
        // Правка форка: каналы (isChannel протаскивается до ConfigureRoom).
        val isChannel: Boolean,
        val parentSpaceId: RoomId?,
    ) : NodeInputs, Parcelable

    private val callback: CreateRoomEntryPoint.Callback = callback()

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            // Правка форка: новая группа, как в TG, начинается с выбора людей, потом фото и название;
            // приглашения уходят вместе с созданием комнаты.
            is NavTarget.SelectMembers -> {
                val callback = object : SelectMembersNode.Callback {
                    override fun onMembersSelected(users: List<MatrixUser>) {
                        backstack.push(
                            NavTarget.ConfigureRoom(isSpace = false, isChannel = false, parentSpaceId = navTarget.parentSpaceId, invites = users)
                        )
                    }
                }
                createNode<SelectMembersNode>(buildContext, plugins = listOf(callback))
            }
            is NavTarget.ConfigureRoom -> {
                val inputs = ConfigureRoomNode.Inputs(
                    isSpace = navTarget.isSpace,
                    isChannel = navTarget.isChannel,
                    parentSpaceId = navTarget.parentSpaceId,
                    invites = navTarget.invites,
                )
                val callback = object : ConfigureRoomNode.Callback {
                    override fun onCreateRoomSuccess(roomId: RoomId) {
                        if (navTarget.invites != null) {
                            // Люди уже выбраны на первом шаге.
                            callback.onRoomCreated(roomId)
                        } else {
                            backstack.replace(NavTarget.AddPeople(roomId))
                        }
                    }
                }
                createNode<ConfigureRoomNode>(buildContext, plugins = listOf(inputs, callback))
            }
            is NavTarget.AddPeople -> {
                val inputs = AddPeopleNode.Inputs(navTarget.roomId)
                val callback: AddPeopleNode.Callback = object : AddPeopleNode.Callback {
                    override fun onFinish() {
                        callback.onRoomCreated(navTarget.roomId)
                    }
                }
                createNode<AddPeopleNode>(buildContext, plugins = listOf(inputs, callback))
            }
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView()
    }

    sealed interface NavTarget : Parcelable {
        @Parcelize
        data class SelectMembers(val parentSpaceId: RoomId?) : NavTarget

        /** [invites] — люди с шага [SelectMembers]; null, если его не было (канал, пространство). */
        @Parcelize
        data class ConfigureRoom(
            val isSpace: Boolean,
            val isChannel: Boolean,
            val parentSpaceId: RoomId?,
            val invites: List<MatrixUser>? = null,
        ) : NavTarget

        @Parcelize
        data class AddPeople(val roomId: RoomId) : NavTarget
    }
}

private fun initialElementFromInputs(inputs: CreateRoomFlowNode.Inputs) = if (!inputs.isSpace && !inputs.isChannel) {
    CreateRoomFlowNode.NavTarget.SelectMembers(parentSpaceId = inputs.parentSpaceId)
} else {
    CreateRoomFlowNode.NavTarget.ConfigureRoom(
        isSpace = inputs.isSpace,
        isChannel = inputs.isChannel,
        parentSpaceId = inputs.parentSpaceId,
    )
}
