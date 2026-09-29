/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.createroom.impl.selectmembers

import androidx.compose.foundation.text.input.TextFieldState
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.collections.immutable.ImmutableList

/**
 * Первый шаг «Новой группы», как в TG (`GroupCreateActivity`): выбрать людей, потом — фото и
 * название. Без поиска список — недавние собеседники, с поиском — каталог пользователей сервера.
 */
data class SelectMembersState(
    val query: TextFieldState,
    val users: ImmutableList<SelectableUser>,
    val selectedUsers: ImmutableList<MatrixUser>,
    val isSearching: Boolean,
    val eventSink: (SelectMembersEvent) -> Unit,
) {
    val showNoResults: Boolean = query.text.isNotBlank() && !isSearching && users.isEmpty()
}

data class SelectableUser(
    val user: MatrixUser,
    /** `@ник` для своего сервера, полный `@ник:сервер` для чужого. */
    val handle: String,
    val isSelected: Boolean,
    val isUnresolved: Boolean,
)

sealed interface SelectMembersEvent {
    data class ToggleUser(val user: MatrixUser) : SelectMembersEvent
}
