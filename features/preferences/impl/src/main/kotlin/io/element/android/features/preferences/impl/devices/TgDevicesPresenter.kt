/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.accountapi.api.AccountDevice
import io.element.android.libraries.accountapi.api.AccountSessionApi
import io.element.android.libraries.accountapi.api.LoginOfferResult
import io.element.android.libraries.accountapi.api.LoginOfferState
import io.element.android.libraries.accountapi.api.LoginQrCode
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.keyescrow.api.PairingKeyTransfer
import io.element.android.libraries.keyescrow.api.RecoveryKeyAutoProvisioner
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * «Устройства» как в Telegram: список сеансов, завершение чужого сеанса и привязка нового
 * устройства QR-кодом. Всё через сервис account и CS API — без страницы MAS в браузере.
 *
 * Если на этом устройстве есть ключ восстановления, он уходит новому устройству вместе с QR
 * ([PairingKeyTransfer]): шифротекст — серверу с кодом, K — только в QR. Тогда новому устройству
 * не нужен пароль, чтобы открыть историю.
 */
@Inject
class TgDevicesPresenter(
    private val accountSessionApi: AccountSessionApi,
    private val recoveryKeyAutoProvisioner: RecoveryKeyAutoProvisioner,
    private val pairingKeyTransfer: PairingKeyTransfer,
    private val sessionId: SessionId,
) : Presenter<TgDevicesState> {
    // Ключ с устройства берём один раз на экран: его проверка — запрос к серверу, а QR обновляется каждые 2 минуты.
    private var recoveryKey: String? = null
    private var recoveryKeyLoaded = false

    @Composable
    override fun present(): TgDevicesState {
        val scope = rememberCoroutineScope()
        var devices by remember { mutableStateOf<List<AccountDevice>?>(null) }
        var loadFailed by remember { mutableStateOf(false) }
        var link by remember { mutableStateOf<TgDeviceLink>(TgDeviceLink.Closed) }
        var deviceToEnd by remember { mutableStateOf<AccountDevice?>(null) }
        var endingDeviceId by remember { mutableStateOf<String?>(null) }
        var endFailed by remember { mutableStateOf(false) }

        suspend fun load() {
            val loaded = accountSessionApi.listDevices()
            loadFailed = loaded == null
            // Это устройство — первым, дальше по давности.
            if (loaded != null) devices = loaded.sortedWith(compareByDescending<AccountDevice> { it.isCurrent }.thenByDescending { it.lastSeenTimestamp ?: 0L })
        }

        LaunchedEffect(Unit) { load() }

        // Пока QR-код на экране: раз в пару секунд спрашиваем, вошло ли новое устройство. Код
        // истёк — молча берём следующий, как Telegram обновляет свой QR.
        val showing = link as? TgDeviceLink.Showing
        LaunchedEffect(showing?.qrData) {
            val code = showing?.qrData?.let(LoginQrCode::decode)?.code ?: return@LaunchedEffect
            while (true) {
                delay(POLL_MILLIS)
                when (accountSessionApi.loginOfferState(code)) {
                    LoginOfferState.Redeemed -> {
                        link = TgDeviceLink.Linked
                        load()
                        return@LaunchedEffect
                    }
                    LoginOfferState.Expired -> {
                        link = newOffer()
                        return@LaunchedEffect
                    }
                    LoginOfferState.Waiting, null -> Unit
                }
            }
        }

        fun handleEvent(event: TgDevicesEvent) {
            when (event) {
                TgDevicesEvent.Refresh -> scope.launch { load() }
                TgDevicesEvent.OpenLink -> if (link !is TgDeviceLink.Loading) {
                    link = TgDeviceLink.Loading
                    scope.launch { link = newOffer() }
                }
                TgDevicesEvent.CloseLink -> {
                    // Новое устройство появляется в списке сервера с первым своим запросом — он мог
                    // прийти уже после того, как код погашен. Перечитываем при возврате к списку.
                    val wasLinked = link == TgDeviceLink.Linked
                    link = TgDeviceLink.Closed
                    if (wasLinked) scope.launch { load() }
                }
                is TgDevicesEvent.AskEnd -> {
                    endFailed = false
                    deviceToEnd = event.device
                }
                TgDevicesEvent.DismissEnd -> deviceToEnd = null
                TgDevicesEvent.ConfirmEnd -> {
                    val device = deviceToEnd ?: return
                    deviceToEnd = null
                    endingDeviceId = device.deviceId
                    scope.launch {
                        if (accountSessionApi.endSession(device.deviceId)) {
                            devices = devices?.filterNot { it.deviceId == device.deviceId }
                        } else {
                            endFailed = true
                        }
                        endingDeviceId = null
                    }
                }
            }
        }

        return TgDevicesState(
            devices = devices,
            loadFailed = loadFailed,
            link = link,
            deviceToEnd = deviceToEnd,
            endingDeviceId = endingDeviceId,
            endFailed = endFailed,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun newOffer(): TgDeviceLink {
        // Без ключа на устройстве — QR как раньше, новое устройство попросит пароль.
        val sealed = recoveryKeyOnDevice()?.let { key ->
            runCatching { pairingKeyTransfer.seal(key, sessionId.value) }
                .onFailure { Timber.w(it, "Could not seal the recovery key for pairing") }
                .getOrNull()
        }
        return when (val offer = accountSessionApi.createLoginOffer(sealed?.ciphertext)) {
            is LoginOfferResult.Offer -> TgDeviceLink.Showing(LoginQrCode.encode(offer.code, sealed?.key))
            is LoginOfferResult.Failure -> TgDeviceLink.Failed
        }
    }

    private suspend fun recoveryKeyOnDevice(): String? {
        if (!recoveryKeyLoaded) {
            recoveryKey = runCatching { recoveryKeyAutoProvisioner.recoveryKeyOnDevice() }.getOrNull()
            recoveryKeyLoaded = true
        }
        return recoveryKey
    }

    private companion object {
        const val POLL_MILLIS = 2_000L
    }
}
