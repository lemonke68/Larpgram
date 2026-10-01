/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.devices

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.root.TgSettingsColors
import io.element.android.features.preferences.impl.root.TgSettingsGroup
import io.element.android.features.preferences.impl.root.TgSettingsItem
import io.element.android.features.preferences.impl.root.tgSettingsPageColor
import io.element.android.libraries.accountapi.api.AccountDevice
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.qrcode.QrCodeImage
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TgDevicesView(
    state: TgDevicesState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLinking = state.link != TgDeviceLink.Closed
    // С QR-кода «назад» возвращает к списку, а не закрывает экран.
    BackHandler(enabled = isLinking) { state.eventSink(TgDevicesEvent.CloseLink) }

    PreferencePage(
        modifier = modifier,
        onBackClick = { if (isLinking) state.eventSink(TgDevicesEvent.CloseLink) else onBackClick() },
        title = stringResource(if (isLinking) R.string.larpgram_devices_link_title else R.string.larpgram_settings_devices_title),
        containerColor = tgSettingsPageColor(),
    ) {
        if (isLinking) {
            LinkDevice(state)
        } else {
            DeviceList(state)
        }
    }

    state.deviceToEnd?.let { device ->
        ConfirmationDialog(
            title = stringResource(R.string.larpgram_devices_end_title),
            content = stringResource(R.string.larpgram_devices_end_message, device.title()),
            submitText = stringResource(R.string.larpgram_devices_end_action),
            destructiveSubmit = true,
            onSubmitClick = { state.eventSink(TgDevicesEvent.ConfirmEnd) },
            onDismiss = { state.eventSink(TgDevicesEvent.DismissEnd) },
        )
    }
}

@Composable
private fun DeviceList(state: TgDevicesState) {
    Column {
        TgSettingsGroup {
            TgSettingsItem(
                title = stringResource(R.string.larpgram_devices_link_action),
                subtitle = stringResource(R.string.larpgram_devices_link_hint),
                color = TgSettingsColors.Cyan,
                iconVector = CompoundIcons.QrCode(),
                onClick = { state.eventSink(TgDevicesEvent.OpenLink) },
            )
        }
        val devices = state.devices
        when {
            devices == null && state.loadFailed -> Note(stringResource(R.string.larpgram_devices_load_failed)) {
                TextButton(text = stringResource(CommonStrings.action_retry), onClick = { state.eventSink(TgDevicesEvent.Refresh) })
            }
            devices == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {
                SectionTitle(stringResource(R.string.larpgram_devices_this_device))
                TgSettingsGroup {
                    devices.filter { it.isCurrent }.forEach { DeviceRow(it, state) }
                }
                val others = devices.filterNot { it.isCurrent }
                if (others.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.larpgram_devices_other_devices))
                    TgSettingsGroup {
                        others.forEach { DeviceRow(it, state) }
                    }
                    Text(
                        text = stringResource(R.string.larpgram_devices_other_hint),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                    )
                }
                if (state.endFailed) {
                    Text(
                        text = stringResource(R.string.larpgram_devices_end_failed),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textCriticalPrimary,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: AccountDevice, state: TgDevicesState) {
    val lastSeen = device.lastSeenTimestamp?.let {
        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
    TgSettingsItem(
        title = device.title(),
        subtitle = when {
            device.isCurrent -> stringResource(R.string.larpgram_devices_online)
            lastSeen != null -> stringResource(R.string.larpgram_devices_last_seen, lastSeen)
            else -> null
        },
        color = if (device.isCurrent) TgSettingsColors.Green else TgSettingsColors.Blue,
        iconVector = CompoundIcons.Mobile(),
        trailingContent = when {
            device.isCurrent -> null
            state.endingDeviceId == device.deviceId -> ListItemContent.Custom { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            else -> ListItemContent.Text(stringResource(R.string.larpgram_devices_end_action))
        },
        style = ListItemStyle.Default,
        // Свой сеанс завершается выходом из аккаунта, а не отсюда.
        onClick = { if (!device.isCurrent && state.endingDeviceId == null) state.eventSink(TgDevicesEvent.AskEnd(device)) },
    )
}

@Composable
private fun AccountDevice.title(): String = displayName ?: stringResource(R.string.larpgram_devices_unnamed, deviceId)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = ElementTheme.typography.fontBodyMdMedium,
        color = ElementTheme.colors.textActionAccent,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun Note(text: String, action: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

@Composable
private fun LinkDevice(state: TgDevicesState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (val link = state.link) {
            is TgDeviceLink.Showing -> {
                // QR-код всегда на белом: тёмный фон камеры читают хуже.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(16.dp),
                ) {
                    QrCodeImage(data = link.qrData, modifier = Modifier.fillMaxWidth())
                }
                Steps()
            }
            TgDeviceLink.Linked -> Note(stringResource(R.string.larpgram_devices_link_done)) {
                TextButton(text = stringResource(CommonStrings.action_done), onClick = { state.eventSink(TgDevicesEvent.CloseLink) })
            }
            TgDeviceLink.Failed -> Note(stringResource(R.string.larpgram_devices_link_failed)) {
                TextButton(text = stringResource(CommonStrings.action_retry), onClick = { state.eventSink(TgDevicesEvent.OpenLink) })
            }
            TgDeviceLink.Loading, TgDeviceLink.Closed -> Box(Modifier.padding(48.dp)) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun Steps() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            R.string.larpgram_devices_link_step1,
            R.string.larpgram_devices_link_step2,
            R.string.larpgram_devices_link_step3,
        ).forEachIndexed { index, res ->
            Text(
                text = "${index + 1}. ${stringResource(res)}",
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textPrimary,
            )
        }
        Text(
            text = stringResource(R.string.larpgram_devices_link_warning),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

internal class TgDevicesStatePreviewParam : PreviewParameterProvider<TgDevicesState> {
    private val devices = listOf(
        AccountDevice("AAAAAAAAAA", "Larpgram Android (HUAWEI MAR-LX1M)", null, isCurrent = true),
        AccountDevice("BBBBBBBBBB", "Larpgram Android", 1_700_000_000_000, isCurrent = false),
        AccountDevice("CCCCCCCCCC", null, null, isCurrent = false),
    )
    override val values: Sequence<TgDevicesState>
        get() = sequenceOf(
            aTgDevicesState(devices = devices),
            aTgDevicesState(devices = null),
            aTgDevicesState(devices = null, loadFailed = true),
            aTgDevicesState(devices = devices, link = TgDeviceLink.Showing("larpgram-login:preview")),
            aTgDevicesState(devices = devices, link = TgDeviceLink.Linked),
            aTgDevicesState(devices = devices, deviceToEnd = devices[1]),
        )
}

internal fun aTgDevicesState(
    devices: List<AccountDevice>? = emptyList(),
    loadFailed: Boolean = false,
    link: TgDeviceLink = TgDeviceLink.Closed,
    deviceToEnd: AccountDevice? = null,
    endingDeviceId: String? = null,
    endFailed: Boolean = false,
    eventSink: (TgDevicesEvent) -> Unit = {},
) = TgDevicesState(
    devices = devices,
    loadFailed = loadFailed,
    link = link,
    deviceToEnd = deviceToEnd,
    endingDeviceId = endingDeviceId,
    endFailed = endFailed,
    eventSink = eventSink,
)

@PreviewsDayNight
@Composable
internal fun TgDevicesViewPreview(
    @PreviewParameter(TgDevicesStatePreviewParam::class) state: TgDevicesState,
) = ElementPreview {
    TgDevicesView(state = state, onBackClick = {})
}
