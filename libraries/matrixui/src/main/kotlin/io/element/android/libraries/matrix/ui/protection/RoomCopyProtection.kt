/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.matrix.ui.protection

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.androidutils.window.SecureWindowReasons
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive

@Inject
@SingleIn(SessionScope::class)
class RoomCopyProtection(private val client: MatrixClient) {
    private val states = MutableStateFlow<Map<RoomId, Boolean>>(emptyMap())
    val allowed = states.asStateFlow()

    suspend fun read(roomId: RoomId): Result<Boolean> = client.getRoomCopyProtection(roomId).also { result ->
        states.update { it + (roomId to !result.getOrDefault(true)) }
    }

    suspend fun set(room: JoinedRoom, enabled: Boolean): Result<Unit> =
        room.sendRawStateEvent(CONTENT_PROTECTION_EVENT, "", "{\"enabled\":$enabled}").onSuccess {
            // A successful local change updates every visible screen immediately, without waiting
            // for the polling interval or a custom-state sync subscription.
            states.update { it + (room.roomId to !enabled) }
        }

    suspend fun isAllowed(roomId: RoomId): Boolean = !read(roomId).getOrDefault(true)
    suspend fun requireAllowed(roomId: RoomId) {
        check(!read(roomId).getOrThrow()) { "Copying from this channel is restricted" }
    }
}

/** Fail closed on entry/resume and errors. Actions recheck immediately before export. */
@Composable
fun rememberRoomCopyAllowed(protection: RoomCopyProtection, room: BaseRoom): Boolean {
    val info by room.roomInfoFlow.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val states by protection.allowed.collectAsState()
    var checked by remember(room.roomId, info.isPublic, info.roomPowerLevels, lifecycle) { mutableStateOf(false) }
    LaunchedEffect(protection, room.roomId, info.isPublic, info.roomPowerLevels, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            checked = false
            while (isActive) {
                protection.read(room.roomId)
                checked = true
                delay(30_000)
            }
        }
    }
    return checked && states[room.roomId] == true
}

@Composable
fun ProtectedRoomWindow(protected: Boolean) {
    val window = LocalContext.current.activityOrNull()?.window
    DisposableEffect(window, protected) {
        val reason = Any()
        if (window != null && protected) SecureWindowReasons.set(window, reason, true)
        onDispose { if (window != null && protected) SecureWindowReasons.set(window, reason, false) }
    }
}

private tailrec fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}

val LocalRoomCopyAllowed = staticCompositionLocalOf { true }

internal const val CONTENT_PROTECTION_EVENT = "ru.mangokokos.larpgram.content_protection"
