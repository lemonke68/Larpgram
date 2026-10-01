/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.account.TgAccountEmailState.Step
import io.element.android.libraries.accountapi.api.AccountEmail
import io.element.android.libraries.designsystem.components.tg.TgFormPrimaryButton
import io.element.android.libraries.designsystem.components.tg.TgFormTextField
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TgAccountEmailView(
    state: TgAccountEmailState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onBack = { if (state.step == Step.Code) state.eventSink(TgAccountEmailEvent.Back) else onBackClick() }
    BackHandler(enabled = state.step == Step.Code, onBack = onBack)

    val current = (state.currentEmail as? AccountEmail.Address)?.email
    when (state.step) {
        Step.Enter -> TgAccountPage(
            title = stringResource(R.string.larpgram_account_email_title),
            subtitle = if (current != null) {
                stringResource(R.string.larpgram_account_email_subtitle_change, current)
            } else {
                stringResource(R.string.larpgram_account_email_subtitle_new)
            },
            onBackClick = onBack,
            modifier = modifier,
        ) {
            val error = state.error
            fun submit() {
                if (state.canSubmit) state.eventSink(TgAccountEmailEvent.Submit)
            }
            TgFormTextField(
                value = state.email,
                onValueChange = { state.eventSink(TgAccountEmailEvent.SetEmail(it)) },
                label = stringResource(if (current != null) R.string.larpgram_account_email_field_new else R.string.larpgram_account_email_field),
                error = error?.text(),
                enabled = !state.isBusy,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                onDone = ::submit,
                contentType = ContentType.EmailAddress,
            )
            Spacer(Modifier.height(20.dp))
            TgFormPrimaryButton(
                text = stringResource(R.string.larpgram_account_email_submit),
                onClick = ::submit,
                enabled = state.canSubmit,
                showProgress = state.isBusy,
            )
        }
        Step.Code -> TgAccountPage(
            title = stringResource(R.string.larpgram_account_code_title),
            subtitle = stringResource(R.string.larpgram_account_code_subtitle, state.email.trim()),
            onBackClick = onBack,
            modifier = modifier,
        ) {
            TgAccountCodeEntry(
                code = state.code,
                resendAfterSeconds = state.resendAfterSeconds,
                codeSentCount = state.codeSentCount,
                isBusy = state.isBusy,
                error = state.error,
                onCodeChange = { state.eventSink(TgAccountEmailEvent.SetCode(it)) },
                onResendClick = { state.eventSink(TgAccountEmailEvent.ResendCode) },
            )
        }
        Step.Done -> TgAccountPage(
            title = stringResource(R.string.larpgram_account_email_title),
            subtitle = stringResource(R.string.larpgram_account_email_done, current.orEmpty()),
            onBackClick = onBackClick,
            modifier = modifier,
        ) {
            TgFormPrimaryButton(text = stringResource(CommonStrings.action_done), onClick = onBackClick)
        }
    }
}

internal class TgAccountEmailStatePreviewParam : PreviewParameterProvider<TgAccountEmailState> {
    override val values: Sequence<TgAccountEmailState>
        get() = sequenceOf(
            aTgAccountEmailState(),
            aTgAccountEmailState(
                currentEmail = AccountEmail.Address("old@example.com"),
                email = "nope",
                error = TgAccountError(TgAccountError.Kind.EmailInvalid),
            ),
            aTgAccountEmailState(step = Step.Code, email = "new@example.com", code = "12", resendAfterSeconds = 40),
            aTgAccountEmailState(step = Step.Done, currentEmail = AccountEmail.Address("new@example.com")),
        )
}

internal fun aTgAccountEmailState(
    step: Step = Step.Enter,
    currentEmail: AccountEmail? = AccountEmail.None,
    email: String = "",
    code: String = "",
    resendAfterSeconds: Int = 0,
    isBusy: Boolean = false,
    error: TgAccountError? = null,
) = TgAccountEmailState(
    step = step,
    currentEmail = currentEmail,
    email = email,
    code = code,
    resendAfterSeconds = resendAfterSeconds,
    codeSentCount = 0,
    isBusy = isBusy,
    error = error,
    eventSink = {},
)

@PreviewsDayNight
@Composable
internal fun TgAccountEmailViewPreview(
    @PreviewParameter(TgAccountEmailStatePreviewParam::class) state: TgAccountEmailState,
) = ElementPreview {
    TgAccountEmailView(state = state, onBackClick = {})
}
