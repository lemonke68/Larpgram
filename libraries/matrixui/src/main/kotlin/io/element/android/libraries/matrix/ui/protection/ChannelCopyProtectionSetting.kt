/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.matrix.ui.protection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.powerlevels.permissionsAsState
import io.element.android.libraries.matrix.ui.R
import kotlinx.coroutines.launch

@Composable
fun ChannelCopyProtectionSetting(protection: RoomCopyProtection, room: BaseRoom) {
    val info by room.roomInfoFlow.collectAsState()
    val canChange by room.permissionsAsState(false) { it.canOwnUserSendState(StateEventType.Custom(CONTENT_PROTECTION_EVENT)) }
    if (room !is JoinedRoom || info.isPublic == true || (info.roomPowerLevels?.values?.eventsDefault ?: 0) <= 0 ||
        !canChange
    ) {
        return
    }
    var enabled by remember(room.roomId) { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(room.roomId, refresh) {
        error = false
        protection.read(room.roomId)
            .onSuccess { enabled = it }
            .onFailure { error = true }
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.larpgram_copy_protection_title), modifier = Modifier.weight(1f))
            Switch(
                checked = enabled == true,
                enabled = enabled != null && !busy,
                onCheckedChange = { next ->
                    if (!busy) {
                        busy = true
                        error = false
                        scope.launch {
                            protection.set(room, next)
                                .onSuccess { enabled = next }
                                .onFailure { error = true }
                            busy = false
                        }
                    }
                },
            )
        }
        Text(stringResource(R.string.larpgram_copy_protection_note), style = ElementTheme.typography.fontBodySmRegular)
        if (error) TextButton(onClick = { refresh++ }, enabled = !busy) { Text(stringResource(R.string.larpgram_copy_protection_retry)) }
    }
}
