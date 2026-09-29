/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.createroom.impl.selectmembers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.createroom.impl.R
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.FloatingActionButton
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.SearchField
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.CheckableUserRow
import io.element.android.libraries.matrix.ui.components.CheckableUserRowData
import io.element.android.libraries.matrix.ui.components.SelectedUsersRowList
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.model.getBestName
import io.element.android.libraries.ui.strings.CommonPlurals
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList

/**
 * «Новая группа» TG (`GroupCreateActivity`): в шапке число выбранных, под ней выбранные и поле
 * поиска, ниже список с галочками; стрелка внизу справа ведёт к фото и названию. Группу можно
 * создать и без участников, как в TG, поэтому стрелка есть всегда.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectMembersView(
    state: SelectMembersState,
    onBackClick: () -> Unit,
    onNextClick: (ImmutableList<MatrixUser>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(CommonStrings.larpgram_new_group),
                            style = ElementTheme.typography.fontHeadingSmMedium,
                        )
                        Text(
                            text = if (state.selectedUsers.isEmpty()) {
                                stringResource(R.string.larpgram_select_members_subtitle)
                            } else {
                                pluralStringResource(CommonPlurals.larpgram_member_count, state.selectedUsers.size, state.selectedUsers.size)
                            },
                            style = ElementTheme.typography.fontBodySmRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    }
                },
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.imePadding(),
                shape = CircleShape,
                containerColor = ElementTheme.colors.bgAccentRest,
                contentColor = Color.White,
                onClick = { onNextClick(state.selectedUsers) },
            ) {
                Icon(
                    imageVector = CompoundIcons.ArrowRight(),
                    contentDescription = stringResource(CommonStrings.action_next),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (state.selectedUsers.isNotEmpty()) {
                SelectedUsersRowList(
                    selectedUsers = state.selectedUsers,
                    onUserRemove = { state.eventSink(SelectMembersEvent.ToggleUser(it)) },
                    autoScroll = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            SearchField(
                state = state.query,
                placeholder = stringResource(R.string.larpgram_select_members_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.showNoResults) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(CommonStrings.common_no_results),
                        color = ElementTheme.colors.textSecondary,
                        style = ElementTheme.typography.fontBodyLgRegular,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(state.users, key = { it.user.userId.value }) { item ->
                    CheckableUserRow(
                        checked = item.isSelected,
                        onCheckedChange = { state.eventSink(SelectMembersEvent.ToggleUser(item.user)) },
                        data = if (item.isUnresolved) {
                            CheckableUserRowData.Unresolved(
                                avatarData = item.user.getAvatarData(AvatarSize.UserListItem),
                                id = item.handle,
                            )
                        } else {
                            CheckableUserRowData.Resolved(
                                avatarData = item.user.getAvatarData(AvatarSize.UserListItem),
                                name = item.user.getBestName(),
                                subtext = item.handle,
                            )
                        },
                    )
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun SelectMembersViewPreview(@PreviewParameter(SelectMembersStatePreviewParam::class) state: SelectMembersState) = ElementPreview {
    SelectMembersView(
        state = state,
        onBackClick = {},
        onNextClick = {},
        modifier = Modifier.navigationBarsPadding(),
    )
}
