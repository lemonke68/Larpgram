/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.SearchField
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.OnVisibleRangeChangeEffect
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Поиск в чате: поле в шапке, ниже совпадения как строки списка чатов TG (аватар и имя
 * отправителя, время, текст); тап открывает сообщение в ленте.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchView(
    state: ChatSearchState,
    onBackClick: () -> Unit,
    onResultClick: (EventId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onClick = onBackClick) },
                title = { ChatSearchField(state.query) },
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        OnVisibleRangeChangeEffect(listState) { state.eventSink(ChatSearchEvent.VisibleRangeChanged(it)) }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                !state.isAvailable -> item { ChatSearchHint(stringResource(R.string.larpgram_chat_search_unavailable)) }
                state.showNoResults -> item {
                    ChatSearchHint(
                        title = stringResource(R.string.larpgram_chat_search_no_results),
                        text = stringResource(CommonStrings.larpgram_message_search_scope_hint),
                    )
                }
            }
            items(state.results, key = { it.eventId.value }) { result ->
                ChatSearchResultRow(result = result, onClick = { onResultClick(result.eventId) })
            }
            if (state.isLoading) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .wrapContentWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSearchField(query: TextFieldState) {
    val focusRequester = remember { FocusRequester() }
    SearchField(
        state = query,
        placeholder = stringResource(R.string.larpgram_chat_search_hint),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp)
            .focusRequester(focusRequester),
    )
    if (!LocalInspectionMode.current) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
private fun ChatSearchResultRow(result: ChatSearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(avatarData = result.senderAvatar, avatarType = AvatarType.User)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = result.senderName,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ElementTheme.typography.fontBodyLgMedium,
                )
                Text(
                    text = result.formattedTimestamp,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            Text(
                text = result.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ChatSearchHint(title: String, text: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            textAlign = TextAlign.Center,
            style = ElementTheme.typography.fontBodyLgMedium,
        )
        if (text != null) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ChatSearchViewPreview(@PreviewParameter(ChatSearchStatePreviewParam::class) state: ChatSearchState) = ElementPreview {
    ChatSearchView(state = state, onBackClick = {}, onResultClick = {})
}
