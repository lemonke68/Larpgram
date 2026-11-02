/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.matrix.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.ForwardEventException
import io.element.android.libraries.matrix.ui.R
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.saved.SavedMessages
import io.element.android.libraries.matrix.ui.saved.SavedMessagesAvatar
import kotlinx.coroutines.launch

/** Internal media sharing. The caller owns the source policy and the actual send operation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TgShareSheet(
    client: MatrixClient,
    savedMessages: SavedMessages,
    onDismiss: () -> Unit,
    onSend: suspend (List<RoomId>, String) -> Result<Unit>,
    onShareExternally: suspend () -> Result<Unit>,
    secondaryAction: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    // Read the activity's insets before opening the dialog: legacy dialog windows report zero
    // navigation insets even while the system navigation area still clips their content.
    val navigationBottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val rooms by client.roomListService.allRooms.summaries.collectAsState(emptyList())
    val savedId by savedMessages.roomId.collectAsState()
    val savedName = stringResource(R.string.larpgram_share_saved)
    val recipients = remember(rooms, savedId, savedName) {
        listOf(ShareRecipient(
            SAVED_KEY,
            savedId,
            savedName,
            rooms.firstOrNull { it.roomId == savedId }?.info?.getAvatarData(AvatarSize.SelectedRoom)
                ?: AvatarData(SAVED_KEY, savedName, size = AvatarSize.SelectedRoom)
        )) +
            rooms.filter { !it.info.isSpace && it.info.currentUserMembership == CurrentUserMembership.JOINED && it.roomId != savedId }
                .sortedByDescending { it.latestEventTimestamp }
                .map { ShareRecipient(it.roomId.value, it.roomId, it.info.name ?: it.roomId.value, it.info.getAvatarData(AvatarSize.SelectedRoom)) }
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    // Keep successful recipients disabled after a partial failure, so retry cannot send twice.
    var accepted by remember { mutableStateOf(setOf<RoomId>()) }
    var caption by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selectedRecipients = recipients.filter { it.key in selected }

    fun externalShare() {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            val result = runCatchingExceptions { onShareExternally().getOrThrow() }
            busy = false
            if (result.isSuccess) onDismiss() else failed = true
        }
    }

    fun send() {
        if (busy || selectedRecipients.isEmpty()) return
        busy = true
        failed = false
        val selection = selectedRecipients
        val sentCaption = caption
        scope.launch {
            val resolved = mutableMapOf<String, RoomId>()
            val result = runCatchingExceptions {
                selection.forEach { recipient ->
                    resolved[recipient.key] = if (recipient.key == SAVED_KEY) savedMessages.getOrCreate().getOrThrow() else requireNotNull(recipient.roomId)
                }
                onSend(resolved.values.distinct().filterNot { it in accepted }, sentCaption).getOrThrow()
            }
            busy = false
            if (result.isSuccess) {
                onDismiss()
            } else {
                val failedIds = (result.exceptionOrNull() as? ForwardEventException)?.roomIds?.toSet()
                if (failedIds != null) {
                    accepted = accepted + resolved.values.filterNot { it in failedIds }
                    selected = resolved.filterValues { it in failedIds }.keys
                }
                failed = true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy || it != SheetValue.Hidden }),
        scrollable = false,
    ) {
        val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(
            Modifier.fillMaxWidth().imePadding().padding(bottom = if (keyboardOpen) 0.dp else maxOf(navigationBottom, 16.dp)).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.larpgram_share_title), style = ElementTheme.typography.fontHeadingMdBold)
                    Text(
                        when (selectedRecipients.size) {
                            0 -> stringResource(R.string.larpgram_share_choose)
                            1 -> selectedRecipients.first().name
                            else -> stringResource(R.string.larpgram_share_selected, selectedRecipients.size)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = ::externalShare, enabled = !busy) {
                    Icon(CompoundIcons.ShareAndroid(), stringResource(R.string.larpgram_share_external))
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                enabled = !busy,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.larpgram_share_search)) },
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp).weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(recipients.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }, key = { it.key }) { recipient ->
                    ShareRecipientView(
                        recipient = recipient,
                        selected = recipient.key in selected,
                        enabled = !busy && recipient.roomId !in accepted,
                        onClick = { selected = if (recipient.key in selected) selected - recipient.key else selected + recipient.key },
                    )
                }
            }
            if (failed) Text(stringResource(R.string.larpgram_share_failed), color = ElementTheme.colors.textCriticalPrimary)
            if (selected.isNotEmpty()) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    placeholder = { Text(stringResource(R.string.larpgram_share_caption)) },
                )
                Button(onClick = ::send, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.larpgram_share_send, selected.size))
                }
            } else if (secondaryAction != null && onSecondaryAction != null) {
                TextButton(onClick = onSecondaryAction, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(secondaryAction) }
            }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.larpgram_share_cancel)) }
        }
    }
}

private data class ShareRecipient(val key: String, val roomId: RoomId?, val name: String, val avatar: AvatarData)

@Composable
private fun ShareRecipientView(recipient: ShareRecipient, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.selectable(selected, enabled = enabled, role = Role.Checkbox, onClick = onClick).padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(58.dp).then(if (selected) Modifier.border(2.dp, ElementTheme.colors.iconAccentTertiary, CircleShape) else Modifier)) {
            if (recipient.key == SAVED_KEY) {
                Box(
                    Modifier.align(Alignment.Center).size(50.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF69BFFA), Color(0xFF3D9DE0))), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(SavedMessagesAvatar.icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            } else {
                Avatar(recipient.avatar, AvatarType.User, forcedAvatarSize = 50.dp, modifier = Modifier.align(Alignment.Center))
            }
            if (selected) {
                Icon(
                    CompoundIcons.Check(),
                    null,
                    modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).background(ElementTheme.colors.bgCanvasDefault, CircleShape),
                )
            }
        }
        Text(recipient.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = ElementTheme.typography.fontBodyXsRegular)
    }
}

private const val SAVED_KEY = "larpgram:saved"
