/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.account.TgAccountError.Kind
import io.element.android.features.preferences.impl.root.tgSettingsPageColor
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.tg.TgFormCodeEntry
import io.element.android.libraries.designsystem.theme.components.Text

/** Страница шага: заголовок в шапке, короткое пояснение и содержимое с полями. */
@Composable
internal fun TgAccountPage(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = title,
        containerColor = tgSettingsPageColor(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(
                text = subtitle,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
internal fun TgAccountErrorText(text: String?) {
    if (text == null) return
    Text(
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textCriticalPrimary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
internal fun TgAccountCodeEntry(
    code: String,
    resendAfterSeconds: Int,
    codeSentCount: Int,
    isBusy: Boolean,
    error: TgAccountError?,
    onCodeChange: (String) -> Unit,
    onResendClick: () -> Unit,
) {
    TgFormCodeEntry(
        code = code,
        onCodeChange = onCodeChange,
        onResendClick = onResendClick,
        resendText = stringResource(R.string.larpgram_account_code_resend),
        resendInText = { stringResource(R.string.larpgram_account_code_resend_in, it) },
        resendAfterSeconds = resendAfterSeconds,
        codeSentCount = codeSentCount,
        isBusy = isBusy,
        error = error?.text(),
    )
}

@Composable
internal fun TgAccountError.text(): String {
    val attempts = attemptsLeft
    if (kind == Kind.WrongCode && attempts != null) {
        return pluralStringResource(R.plurals.larpgram_account_error_wrong_code_attempts, attempts, attempts)
    }
    return stringResource(
        when (kind) {
            Kind.EmailInvalid -> R.string.larpgram_account_error_email_invalid
            Kind.EmailTaken -> R.string.larpgram_account_error_email_taken
            Kind.MailFailed -> R.string.larpgram_account_error_mail_failed
            Kind.WrongCode -> R.string.larpgram_account_error_wrong_code
            Kind.CodeExpired -> R.string.larpgram_account_error_code_expired
            Kind.TooManyAttempts -> R.string.larpgram_account_error_too_many_attempts
            Kind.TooManyRequests -> R.string.larpgram_account_error_too_many_requests
            Kind.PasswordTooShort -> R.string.larpgram_account_error_password_too_short
            Kind.PasswordTooLong -> R.string.larpgram_account_error_password_too_long
            Kind.PasswordWeak -> R.string.larpgram_account_error_password_weak
            Kind.PasswordsDiffer -> R.string.larpgram_account_error_passwords_differ
            Kind.Network -> R.string.larpgram_account_error_network
        }
    )
}
