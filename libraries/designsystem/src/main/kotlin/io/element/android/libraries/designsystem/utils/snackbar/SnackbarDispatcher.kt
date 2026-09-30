/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.utils.snackbar

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.element.android.libraries.designsystem.theme.components.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * A global dispatcher of [SnackbarMessage] to be displayed in [Snackbar] via a [SnackbarHostState].
 *
 * The current head of the queue is exposed as a [MutableStateFlow] so that every collector — including
 * hosts that subscribe *after* a message was posted (e.g. a screen recomposing as a flow pops back to
 * it) — observes the current message. An earlier mutex-gated implementation delivered each message to
 * whichever single collector happened to be parked on the lock, which could starve the host that was
 * actually on screen, dropping the snackbar.
 */
class SnackbarDispatcher {
    private val snackBarMessageQueue = ArrayDeque<SnackbarMessage>()
    private val currentMessage = MutableStateFlow<SnackbarMessage?>(null)

    val snackbarMessage: Flow<SnackbarMessage?> = currentMessage.asStateFlow()

    @Synchronized
    fun post(message: SnackbarMessage) {
        snackBarMessageQueue.add(message)
        currentMessage.value = snackBarMessageQueue.firstOrNull()
    }

    /**
     * Правка форка: плашку с отсчётом, которую показывал уходящий экран, отдать следующему.
     * Удаление из меню чата постит «Отменить» ещё на экране чата, тот сразу закрывается, и без
     * этого плашка пропадала вместе с ним — вместе с возможностью отменить.
     */
    @Synchronized
    fun handOver(message: SnackbarMessage) {
        if (snackBarMessageQueue.firstOrNull()?.id != message.id) return
        val fresh = message.copy(id = Random.nextLong(), isDisplayed = AtomicBoolean(false))
        snackBarMessageQueue[0] = fresh
        currentMessage.value = fresh
    }

    @Synchronized
    fun clear() {
        snackBarMessageQueue.removeFirstOrNull()
        currentMessage.value = snackBarMessageQueue.firstOrNull()
    }
}

/** Used to provide a [SnackbarDispatcher] to composable functions, it's needed for [rememberSnackbarHostState]. */
val LocalSnackbarDispatcher = compositionLocalOf { SnackbarDispatcher() }

@Composable
fun SnackbarDispatcher.collectSnackbarMessageAsState(): State<SnackbarMessage?> {
    return snackbarMessage.collectAsState(initial = null)
}

/**
 * Helper method to display a [SnackbarMessage] in a [SnackbarHostState] handling cancellations.
 */
@Composable
fun rememberSnackbarHostState(snackbarMessage: SnackbarMessage?): SnackbarHostState {
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessageText = snackbarMessage?.let {
        stringResource(id = snackbarMessage.messageResId)
    } ?: return snackbarHostState
    // Правка форка: кнопка действия и её обработка. Апстрим не передавал actionResId в плашку
    // и не вызывал action, так что «Отменить» не показывалась нигде.
    val actionLabel = snackbarMessage.actionResId?.let { stringResource(id = it) }

    val dispatcher = LocalSnackbarDispatcher.current
    LaunchedEffect(snackbarMessage.id) {
        // If the message wasn't already displayed, do it now, and mark it as displayed
        // This will prevent the message from appearing in any other active SnackbarHosts
        if (snackbarMessage.isDisplayed.getAndSet(true).not()) {
            try {
                val countdownEndsAt = snackbarMessage.countdownEndsAtMillis
                val result = if (countdownEndsAt != null && actionLabel != null) {
                    snackbarHostState.showSnackbar(
                        TgUndoSnackbarVisuals(
                            message = snackbarMessageText,
                            actionLabel = actionLabel,
                            endsAtMillis = countdownEndsAt,
                            totalMillis = snackbarMessage.countdownTotalMillis,
                        )
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        message = snackbarMessageText,
                        actionLabel = actionLabel,
                        duration = snackbarMessage.duration,
                    )
                }
                if (result == SnackbarResult.ActionPerformed) snackbarMessage.action()
                // The snackbar item was displayed and dismissed, clear its message
                dispatcher.clear()
            } catch (e: CancellationException) {
                // The snackbar was being displayed when the coroutine was cancelled,
                // so we need to clear its message
                // Правка форка: недосчитанную плашку «Отменить» не теряем, её покажет следующий экран.
                val countdownEndsAt = snackbarMessage.countdownEndsAtMillis
                if (countdownEndsAt != null && countdownEndsAt > System.currentTimeMillis()) {
                    dispatcher.handOver(snackbarMessage)
                } else {
                    dispatcher.clear()
                }
                throw e
            }
        }
    }
    return snackbarHostState
}
