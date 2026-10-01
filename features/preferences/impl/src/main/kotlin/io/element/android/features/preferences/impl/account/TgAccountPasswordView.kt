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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.account.TgAccountError.Kind
import io.element.android.features.preferences.impl.account.TgAccountPasswordState.Step
import io.element.android.libraries.accountapi.api.AccountEmail
import io.element.android.libraries.designsystem.components.tg.TgFormPasswordField
import io.element.android.libraries.designsystem.components.tg.TgFormPrimaryButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TgAccountPasswordView(
    state: TgAccountPasswordState,
    onBackClick: () -> Unit,
    onBindEmailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInnerStep = state.step == Step.Code || state.step == Step.NewPassword
    val onBack = { if (isInnerStep) state.eventSink(TgAccountPasswordEvent.Back) else onBackClick() }
    BackHandler(enabled = isInnerStep, onBack = onBack)

    val title = stringResource(R.string.larpgram_account_password_title)
    when (state.step) {
        Step.Intro -> {
            val email = state.email
            TgAccountPage(
                title = title,
                subtitle = when (email) {
                    is AccountEmail.Address -> stringResource(R.string.larpgram_account_password_intro, email.email)
                    AccountEmail.None -> stringResource(R.string.larpgram_account_password_no_email)
                    AccountEmail.Unknown, null -> stringResource(R.string.larpgram_account_password_intro_unknown)
                },
                onBackClick = onBack,
                modifier = modifier,
            ) {
                if (email == AccountEmail.None) {
                    TgFormPrimaryButton(
                        text = stringResource(R.string.larpgram_account_password_bind_email),
                        onClick = onBindEmailClick,
                    )
                } else {
                    TgFormPrimaryButton(
                        text = stringResource(R.string.larpgram_account_password_send_code),
                        onClick = { state.eventSink(TgAccountPasswordEvent.SendCode) },
                        enabled = email != null,
                        showProgress = state.isBusy,
                    )
                }
                TgAccountErrorText(state.error?.text())
            }
        }
        Step.Code -> TgAccountPage(
            title = stringResource(R.string.larpgram_account_code_title),
            subtitle = stringResource(R.string.larpgram_account_password_code_subtitle),
            onBackClick = onBack,
            modifier = modifier,
        ) {
            TgAccountCodeEntry(
                code = state.code,
                resendAfterSeconds = state.resendAfterSeconds,
                codeSentCount = state.codeSentCount,
                isBusy = state.isBusy,
                error = state.error,
                onCodeChange = { state.eventSink(TgAccountPasswordEvent.SetCode(it)) },
                onResendClick = { state.eventSink(TgAccountPasswordEvent.ResendCode) },
            )
        }
        Step.NewPassword -> TgAccountPage(
            title = title,
            subtitle = stringResource(R.string.larpgram_account_password_new_subtitle),
            onBackClick = onBack,
            modifier = modifier,
        ) {
            val error = state.error
            val errorText = error?.text()
            fun submit() {
                if (state.canSubmitPassword) state.eventSink(TgAccountPasswordEvent.SubmitPassword)
            }
            TgFormPasswordField(
                value = state.password,
                onValueChange = { state.eventSink(TgAccountPasswordEvent.SetPassword(it)) },
                label = stringResource(R.string.larpgram_account_password_field),
                hint = stringResource(R.string.larpgram_account_password_hint),
                error = errorText.takeIf { error?.kind in PASSWORD_ERRORS },
                enabled = !state.isBusy,
                isNewPassword = true,
            )
            Spacer(Modifier.height(12.dp))
            TgFormPasswordField(
                value = state.passwordRepeat,
                onValueChange = { state.eventSink(TgAccountPasswordEvent.SetPasswordRepeat(it)) },
                label = stringResource(R.string.larpgram_account_password_repeat_field),
                error = errorText.takeIf { error?.kind == Kind.PasswordsDiffer },
                enabled = !state.isBusy,
                imeAction = ImeAction.Done,
                onDone = ::submit,
                isNewPassword = true,
            )
            TgAccountErrorText(errorText.takeIf { error?.kind !in PASSWORD_ERRORS && error?.kind != Kind.PasswordsDiffer })
            Spacer(Modifier.height(20.dp))
            TgFormPrimaryButton(
                text = stringResource(CommonStrings.action_save),
                onClick = ::submit,
                enabled = state.canSubmitPassword,
                showProgress = state.isBusy,
            )
        }
        Step.Done -> TgAccountPage(
            title = title,
            subtitle = stringResource(R.string.larpgram_account_password_done),
            onBackClick = onBackClick,
            modifier = modifier,
        ) {
            TgFormPrimaryButton(text = stringResource(CommonStrings.action_done), onClick = onBackClick)
        }
    }
}

private val PASSWORD_ERRORS = arrayOf(Kind.PasswordTooShort, Kind.PasswordTooLong, Kind.PasswordWeak)

internal class TgAccountPasswordStatePreviewParam : PreviewParameterProvider<TgAccountPasswordState> {
    override val values: Sequence<TgAccountPasswordState>
        get() = sequenceOf(
            aTgAccountPasswordState(email = AccountEmail.Address("v@example.com")),
            aTgAccountPasswordState(email = AccountEmail.None),
            aTgAccountPasswordState(step = Step.Code, error = TgAccountError(Kind.WrongCode, 3)),
            aTgAccountPasswordState(step = Step.NewPassword, password = "password", error = TgAccountError(Kind.PasswordWeak)),
            aTgAccountPasswordState(step = Step.Done),
        )
}

internal fun aTgAccountPasswordState(
    step: Step = Step.Intro,
    email: AccountEmail? = AccountEmail.Address("v@example.com"),
    code: String = "",
    password: String = "",
    passwordRepeat: String = "",
    isBusy: Boolean = false,
    error: TgAccountError? = null,
) = TgAccountPasswordState(
    step = step,
    email = email,
    code = code,
    resendAfterSeconds = 0,
    codeSentCount = 0,
    password = password,
    passwordRepeat = passwordRepeat,
    isBusy = isBusy,
    error = error,
    eventSink = {},
)

@PreviewsDayNight
@Composable
internal fun TgAccountPasswordViewPreview(
    @PreviewParameter(TgAccountPasswordStatePreviewParam::class) state: TgAccountPasswordState,
) = ElementPreview {
    TgAccountPasswordView(state = state, onBackClick = {}, onBindEmailClick = {})
}
