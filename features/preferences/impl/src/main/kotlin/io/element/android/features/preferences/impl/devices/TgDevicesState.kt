/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.devices

import io.element.android.libraries.accountapi.api.AccountDevice

data class TgDevicesState(
    /** `null` — список ещё грузится. */
    val devices: List<AccountDevice>?,
    val loadFailed: Boolean,
    val link: TgDeviceLink,
    /** Устройство, про которое спрашиваем «завершить сеанс?». */
    val deviceToEnd: AccountDevice?,
    /** Идёт завершение сеанса этого устройства. */
    val endingDeviceId: String?,
    val endFailed: Boolean,
    val eventSink: (TgDevicesEvent) -> Unit,
)

/** Привязка нового устройства: оно сканирует QR-код с этого экрана. */
sealed interface TgDeviceLink {
    data object Closed : TgDeviceLink
    data object Loading : TgDeviceLink

    /** [qrData] — содержимое QR-кода. Код одноразовый; когда истекает, экран берёт новый. */
    data class Showing(val qrData: String) : TgDeviceLink
    data object Linked : TgDeviceLink
    data object Failed : TgDeviceLink
}

sealed interface TgDevicesEvent {
    data object Refresh : TgDevicesEvent
    data object OpenLink : TgDevicesEvent
    data object CloseLink : TgDevicesEvent
    data class AskEnd(val device: AccountDevice) : TgDevicesEvent
    data object ConfirmEnd : TgDevicesEvent
    data object DismissEnd : TgDevicesEvent
}
