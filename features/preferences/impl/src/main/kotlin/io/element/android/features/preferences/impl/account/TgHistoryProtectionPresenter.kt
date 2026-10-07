/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.preferences.impl.account.TgHistoryProtectionState.Error
import io.element.android.features.preferences.impl.account.TgHistoryProtectionState.Status
import io.element.android.libraries.accountapi.api.AccountSessionApi
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.keyescrow.api.RecoveryKeyAutoProvisioner
import kotlinx.coroutines.launch

/**
 * Пароль сначала проверяем на сервере account: опечатка заперла бы ключ паролем, которым никто не
 * войдёт, и новые устройства остались бы без истории. Пароль живёт только в памяти экрана.
 */
@Inject
class TgHistoryProtectionPresenter(
    private val provisioner: RecoveryKeyAutoProvisioner,
    private val accountSessionApi: AccountSessionApi,
) : Presenter<TgHistoryProtectionState> {
    @Composable
    override fun present(): TgHistoryProtectionState {
        val scope = rememberCoroutineScope()
        var refresh by remember { mutableIntStateOf(0) }
        var status by remember { mutableStateOf<Status?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var password by remember { mutableStateOf("") }
        var isBusy by remember { mutableStateOf(false) }
        var recoveryKey by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<Error?>(null) }
        val needsPassword by provisioner.needsPassword.collectAsState()

        LaunchedEffect(refresh) {
            isLoading = true
            status = provisioner.protectionStatus()?.let { Status(it.lockedWithPassword, it.serverRecovery) }
            isLoading = false
        }

        fun submitPassword() = scope.launch {
            isBusy = true
            error = null
            error = when (accountSessionApi.checkPassword(password)) {
                null -> Error.Network
                false -> Error.WrongPassword
                true -> if (provisioner.lockWithPassword(password).isSuccess) {
                    password = ""
                    refresh++
                    null
                } else {
                    Error.Network
                }
            }
            isBusy = false
        }

        fun showKey() = scope.launch {
            isBusy = true
            error = null
            recoveryKey = provisioner.recoveryKeyOnDevice()
            if (recoveryKey == null) error = Error.NoKeyOnDevice
            isBusy = false
        }

        fun setServerRecovery(enabled: Boolean) = scope.launch {
            isBusy = true
            error = null
            if (!provisioner.setServerRecovery(enabled)) {
                error = if (enabled) Error.NoKeyOnDevice else Error.Network
            }
            refresh++
            isBusy = false
        }

        fun handleEvent(event: TgHistoryProtectionEvent) {
            when (event) {
                is TgHistoryProtectionEvent.SetPassword -> {
                    password = event.value
                    error = null
                }
                TgHistoryProtectionEvent.SubmitPassword -> if (!isBusy && password.isNotEmpty()) submitPassword()
                TgHistoryProtectionEvent.ShowKey -> if (!isBusy) showKey()
                TgHistoryProtectionEvent.HideKey -> recoveryKey = null
                is TgHistoryProtectionEvent.SetServerRecovery -> if (!isBusy) setServerRecovery(event.enabled)
                TgHistoryProtectionEvent.Retry -> refresh++
            }
        }

        return TgHistoryProtectionState(
            // Провижинер мог узнать раньше сервера, что ключ надо перезапереть (новый ключ без пароля).
            status = status?.let { if (needsPassword) it.copy(lockedWithPassword = false) else it },
            isLoading = isLoading,
            password = password,
            isBusy = isBusy,
            recoveryKey = recoveryKey,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}
