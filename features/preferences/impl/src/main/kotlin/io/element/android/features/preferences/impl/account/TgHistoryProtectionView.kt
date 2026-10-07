/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.account.TgHistoryProtectionState.Error
import io.element.android.features.preferences.impl.account.TgHistoryProtectionState.Status
import io.element.android.features.preferences.impl.root.TgSettingsColors
import io.element.android.features.preferences.impl.root.TgSettingsGroup
import io.element.android.features.preferences.impl.root.TgSettingsItem
import io.element.android.features.preferences.impl.root.tgSettingsPageColor
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.tg.TgFormPasswordField
import io.element.android.libraries.designsystem.components.tg.TgFormPrimaryButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TgHistoryProtectionView(
    state: TgHistoryProtectionState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(R.string.larpgram_history_title),
        containerColor = tgSettingsPageColor(),
    ) {
        val status = state.status
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(
                text = when {
                    state.isLoading && status == null -> ""
                    status == null -> stringResource(R.string.larpgram_history_unavailable)
                    status.lockedWithPassword -> stringResource(R.string.larpgram_history_protected)
                    else -> stringResource(R.string.larpgram_history_needs_password)
                },
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
            if (status == null && !state.isLoading) {
                Spacer(Modifier.height(20.dp))
                TgFormPrimaryButton(
                    text = stringResource(CommonStrings.action_retry),
                    onClick = { state.eventSink(TgHistoryProtectionEvent.Retry) },
                )
            }
            if (status != null && !status.lockedWithPassword) {
                Spacer(Modifier.height(20.dp))
                TgFormPasswordField(
                    value = state.password,
                    onValueChange = { state.eventSink(TgHistoryProtectionEvent.SetPassword(it)) },
                    label = stringResource(R.string.larpgram_history_password_label),
                    error = state.error?.takeIf { it == Error.WrongPassword }?.let { stringResource(R.string.larpgram_history_error_wrong_password) },
                    enabled = !state.isBusy,
                    imeAction = ImeAction.Done,
                    onDone = { state.eventSink(TgHistoryProtectionEvent.SubmitPassword) },
                )
                Spacer(Modifier.height(16.dp))
                TgFormPrimaryButton(
                    text = stringResource(R.string.larpgram_history_protect),
                    onClick = { state.eventSink(TgHistoryProtectionEvent.SubmitPassword) },
                    enabled = state.canSubmit,
                    showProgress = state.isBusy,
                )
            }
        }
        if (status != null) {
            TgSettingsGroup {
                TgSettingsItem(
                    title = stringResource(
                        if (state.recoveryKey == null) R.string.larpgram_history_show_key else R.string.larpgram_history_hide_key
                    ),
                    color = TgSettingsColors.Green,
                    iconVector = CompoundIcons.Key(),
                    onClick = {
                        state.eventSink(if (state.recoveryKey == null) TgHistoryProtectionEvent.ShowKey else TgHistoryProtectionEvent.HideKey)
                    },
                )
                TgSettingsItem(
                    title = stringResource(R.string.larpgram_history_server_title),
                    subtitle = stringResource(R.string.larpgram_history_server_subtitle),
                    color = TgSettingsColors.Orange,
                    iconVector = CompoundIcons.Host(),
                    trailingContent = ListItemContent.Switch(checked = status.serverRecovery, enabled = !state.isBusy),
                    onClick = { state.eventSink(TgHistoryProtectionEvent.SetServerRecovery(!status.serverRecovery)) },
                )
            }
            state.recoveryKey?.let { key ->
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    SelectionContainer {
                        Text(
                            text = key,
                            style = ElementTheme.typography.fontBodyLgMedium.copy(fontFamily = FontFamily.Monospace),
                            color = ElementTheme.colors.textPrimary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.larpgram_history_key_hint),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
        }
        state.error?.takeIf { it != Error.WrongPassword }?.let { error ->
            Text(
                text = stringResource(
                    when (error) {
                        Error.Network -> R.string.larpgram_history_error_network
                        Error.NoKeyOnDevice -> R.string.larpgram_history_error_no_key
                        Error.WrongPassword -> R.string.larpgram_history_error_wrong_password
                    }
                ),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textCriticalPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

internal class TgHistoryProtectionStatePreviewParam : PreviewParameterProvider<TgHistoryProtectionState> {
    override val values: Sequence<TgHistoryProtectionState>
        get() = sequenceOf(
            aTgHistoryProtectionState(),
            aTgHistoryProtectionState(status = Status(lockedWithPassword = false, serverRecovery = false), password = "secret", error = Error.WrongPassword),
            aTgHistoryProtectionState(
                status = Status(lockedWithPassword = true, serverRecovery = true),
                recoveryKey = "EsTc 5rr1 4fJY BvG1 x8Ci ZcYa 3PdS Xa6A PdKx zEsK q7Ap yupT",
            ),
            aTgHistoryProtectionState(status = null, isLoading = false),
            aTgHistoryProtectionState(error = Error.NoKeyOnDevice),
        )
}

internal fun aTgHistoryProtectionState(
    status: Status? = Status(lockedWithPassword = true, serverRecovery = false),
    isLoading: Boolean = false,
    password: String = "",
    isBusy: Boolean = false,
    recoveryKey: String? = null,
    error: Error? = null,
) = TgHistoryProtectionState(
    status = status,
    isLoading = isLoading,
    password = password,
    isBusy = isBusy,
    recoveryKey = recoveryKey,
    error = error,
    eventSink = {},
)

@PreviewsDayNight
@Composable
internal fun TgHistoryProtectionViewPreview(
    @PreviewParameter(TgHistoryProtectionStatePreviewParam::class) state: TgHistoryProtectionState,
) = ElementPreview {
    TgHistoryProtectionView(state = state, onBackClick = {})
}
