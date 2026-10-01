/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tg

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.features.login.impl.screens.tg.TgAuthError.Kind
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.tg.TgFormCodeEntry
import io.element.android.libraries.designsystem.components.tg.TgFormLink
import io.element.android.libraries.designsystem.components.tg.TgFormPasswordField
import io.element.android.libraries.designsystem.components.tg.TgFormPrimaryButton
import io.element.android.libraries.designsystem.components.tg.TgFormSecondaryButton
import io.element.android.libraries.designsystem.components.tg.TgFormTextField
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.qrcode.QrCodeCameraView
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Вход как в Telegram: приветствие с иконкой, дальше шаги с одной главной кнопкой. Каждый шаг —
 * заголовок, короткая подпись, поля, кнопка; переходы — сдвиг вбок.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TgAuthView(
    state: TgAuthState,
    onCloseClick: () -> Unit,
    onReportProblemClick: () -> Unit,
    onDeveloperSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // С первого шага «назад» уводит наружу: закрывает добавление аккаунта либо сворачивает приложение.
    val isFirstStep = state.step == TgAuthStep.Welcome ||
        state.step == TgAuthStep.Loading ||
        (state.step == TgAuthStep.Login && state.isAddingAccount)
    val focusManager = LocalFocusManager.current

    fun goBack() {
        focusManager.clearFocus(force = true)
        if (isFirstStep) onCloseClick() else state.eventSink(TgAuthEvent.Back)
    }

    BackHandler(enabled = !isFirstStep || state.isAddingAccount, onBack = ::goBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (!isFirstStep || state.isAddingAccount) BackButton(onClick = ::goBack)
                },
                actions = {
                    if (state.step == TgAuthStep.Welcome && state.showDeveloperSettings) {
                        IconButton(onClick = onDeveloperSettingsClick) {
                            Icon(
                                imageVector = CompoundIcons.SettingsSolid(),
                                contentDescription = stringResource(CommonStrings.common_developer_options),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                // Вперёд — новый шаг въезжает справа, назад — слева (порядок шагов = порядок в enum).
                val forward = targetState.ordinal > initialState.ordinal
                if (initialState == TgAuthStep.Loading) {
                    fadeIn() togetherWith fadeOut()
                } else {
                    (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it else it } + fadeOut())
                }
            },
            label = "TgAuthStep",
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) { step ->
            when (step) {
                TgAuthStep.Loading -> Box(Modifier.fillMaxSize())
                TgAuthStep.Welcome -> WelcomeStep(state, onReportProblemClick)
                TgAuthStep.Login -> LoginStep(state)
                TgAuthStep.LoginCode -> CodeStep(
                    state = state,
                    subtitle = stringResource(R.string.larpgram_auth_code_register_subtitle, state.loginEmailHint),
                )
                TgAuthStep.Register -> RegisterStep(state)
                TgAuthStep.RegisterCode -> CodeStep(
                    state = state,
                    subtitle = stringResource(R.string.larpgram_auth_code_register_subtitle, state.registerEmail.trim()),
                )
                TgAuthStep.Forgot -> ForgotStep(state)
                TgAuthStep.ForgotCode -> CodeStep(
                    state = state,
                    subtitle = stringResource(R.string.larpgram_auth_code_forgot_subtitle),
                )
                TgAuthStep.NewPassword -> NewPasswordStep(state)
                TgAuthStep.ScanQr -> ScanQrStep(state)
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    state: TgAuthState,
    onReportProblemClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        if (state.logoResId != null) {
            Image(
                painter = painterResource(state.logoResId),
                contentDescription = null,
                modifier = Modifier.size(128.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        // Строки приветствия форк задаёт в app/src/main/res/values{,-ru}/strings.xml.
        Text(
            text = stringResource(R.string.screen_onboarding_welcome_title),
            style = ElementTheme.typography.fontHeadingLgBold,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.screen_onboarding_welcome_message, state.applicationName),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1.4f))
        TgFormPrimaryButton(
            text = stringResource(R.string.larpgram_auth_welcome_start),
            onClick = { state.eventSink(TgAuthEvent.Start) },
        )
        if (state.canReportBug) {
            Text(
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onReportProblemClick)
                    .padding(16.dp),
                text = stringResource(CommonStrings.common_report_a_problem),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        } else {
            Text(
                modifier = Modifier
                    .clickable(role = Role.Button) { state.eventSink(TgAuthEvent.VersionClick) }
                    .padding(16.dp),
                text = stringResource(R.string.screen_onboarding_app_version, state.version),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

/** Каркас шага с формой: заголовок и подпись сверху, содержимое прокручивается над клавиатурой. */
@Composable
private fun FormStep(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Text(
            text = title,
            style = ElementTheme.typography.fontHeadingLgBold,
            color = ElementTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(20.dp))
        content()
    }
}

/** Ошибка шага, не привязанная к полю (сеть, лимиты, неверный пароль). */
@Composable
private fun StepError(text: String?) {
    if (text == null) return
    Text(
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textCriticalPrimary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun LoginStep(state: TgAuthState) {
    val errorText = state.error?.text()
    fun submit() {
        if (state.canSubmitLogin) state.eventSink(TgAuthEvent.SubmitLogin)
    }
    FormStep(
        title = stringResource(R.string.larpgram_auth_login_title),
        subtitle = stringResource(R.string.larpgram_auth_login_subtitle),
    ) {
        TgFormTextField(
            value = state.login,
            onValueChange = { state.eventSink(TgAuthEvent.SetLogin(it)) },
            label = stringResource(R.string.larpgram_auth_login_field),
            enabled = !state.isBusy,
            keyboardType = KeyboardType.Email,
            contentType = ContentType.Username,
        )
        Spacer(Modifier.height(16.dp))
        TgFormPasswordField(
            value = state.password,
            onValueChange = { state.eventSink(TgAuthEvent.SetPassword(it)) },
            label = stringResource(R.string.larpgram_auth_password_field),
            enabled = !state.isBusy,
            imeAction = ImeAction.Done,
            onDone = ::submit,
        )
        StepError(errorText)
        TgFormLink(
            text = stringResource(R.string.larpgram_auth_forgot),
            onClick = { state.eventSink(TgAuthEvent.OpenForgot) },
            enabled = !state.isBusy,
            modifier = Modifier.align(Alignment.End),
        )
        Spacer(Modifier.height(8.dp))
        TgFormPrimaryButton(
            text = stringResource(R.string.larpgram_auth_sign_in),
            onClick = ::submit,
            enabled = state.canSubmitLogin,
            showProgress = state.isBusy,
        )
        Spacer(Modifier.height(32.dp))
        // Регистрация — заметно: большинство людей приходит сюда без аккаунта.
        Text(
            text = stringResource(R.string.larpgram_auth_no_account),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        TgFormSecondaryButton(
            text = stringResource(R.string.larpgram_auth_register),
            onClick = { state.eventSink(TgAuthEvent.OpenRegister) },
            enabled = !state.isBusy,
        )
        Spacer(Modifier.height(8.dp))
        TgFormLink(
            text = stringResource(R.string.larpgram_auth_qr),
            onClick = { state.eventSink(TgAuthEvent.OpenQrScan) },
            enabled = !state.isBusy,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun RegisterStep(state: TgAuthState) {
    val error = state.error
    val errorText = error?.text()
    fun errorFor(vararg kinds: Kind) = errorText.takeIf { error?.kind in kinds }
    fun submit() {
        if (state.canSubmitRegister) state.eventSink(TgAuthEvent.SubmitRegister)
    }
    FormStep(
        title = stringResource(R.string.larpgram_auth_register_title),
        subtitle = stringResource(R.string.larpgram_auth_register_subtitle),
    ) {
        TgFormTextField(
            value = state.registerUsername,
            onValueChange = { state.eventSink(TgAuthEvent.SetRegisterUsername(it)) },
            label = stringResource(R.string.larpgram_auth_username_field),
            hint = stringResource(R.string.larpgram_auth_username_hint),
            error = errorFor(*USERNAME_ERRORS),
            enabled = !state.isBusy,
            keyboardType = KeyboardType.Ascii,
            contentType = ContentType.NewUsername,
        )
        Spacer(Modifier.height(10.dp))
        TgFormTextField(
            value = state.registerEmail,
            onValueChange = { state.eventSink(TgAuthEvent.SetRegisterEmail(it)) },
            label = stringResource(R.string.larpgram_auth_email_field),
            hint = stringResource(R.string.larpgram_auth_email_hint),
            error = errorFor(*EMAIL_ERRORS),
            enabled = !state.isBusy,
            keyboardType = KeyboardType.Email,
            contentType = ContentType.EmailAddress,
        )
        Spacer(Modifier.height(10.dp))
        TgFormPasswordField(
            value = state.registerPassword,
            onValueChange = { state.eventSink(TgAuthEvent.SetRegisterPassword(it)) },
            label = stringResource(R.string.larpgram_auth_password_field),
            hint = stringResource(R.string.larpgram_auth_password_hint),
            error = errorFor(*PASSWORD_ERRORS),
            enabled = !state.isBusy,
            isNewPassword = true,
        )
        Spacer(Modifier.height(10.dp))
        TgFormPasswordField(
            value = state.registerPasswordRepeat,
            onValueChange = { state.eventSink(TgAuthEvent.SetRegisterPasswordRepeat(it)) },
            label = stringResource(R.string.larpgram_auth_password_repeat_field),
            error = errorFor(Kind.PasswordsDiffer),
            enabled = !state.isBusy,
            imeAction = ImeAction.Done,
            onDone = ::submit,
            isNewPassword = true,
        )
        StepError(errorText.takeIf { error?.kind !in FIELD_ERRORS })
        Spacer(Modifier.height(18.dp))
        TgFormPrimaryButton(
            text = stringResource(CommonStrings.action_continue),
            onClick = ::submit,
            enabled = state.canSubmitRegister,
            showProgress = state.isBusy,
        )
    }
}

@Composable
private fun ForgotStep(state: TgAuthState) {
    fun submit() {
        if (state.canSubmitForgot) state.eventSink(TgAuthEvent.SubmitForgot)
    }
    FormStep(
        title = stringResource(R.string.larpgram_auth_forgot_title),
        subtitle = stringResource(R.string.larpgram_auth_forgot_subtitle),
    ) {
        TgFormTextField(
            value = state.forgotLogin,
            onValueChange = { state.eventSink(TgAuthEvent.SetForgotLogin(it)) },
            label = stringResource(R.string.larpgram_auth_login_field),
            enabled = !state.isBusy,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onDone = ::submit,
            contentType = ContentType.Username,
        )
        StepError(state.error?.text())
        Spacer(Modifier.height(24.dp))
        TgFormPrimaryButton(
            text = stringResource(R.string.larpgram_auth_forgot_submit),
            onClick = ::submit,
            enabled = state.canSubmitForgot,
            showProgress = state.isBusy,
        )
    }
}

@Composable
private fun CodeStep(
    state: TgAuthState,
    subtitle: String,
) {
    FormStep(
        title = stringResource(R.string.larpgram_auth_code_title),
        subtitle = subtitle,
    ) {
        TgFormCodeEntry(
            code = state.code,
            onCodeChange = { state.eventSink(TgAuthEvent.SetCode(it)) },
            onResendClick = { state.eventSink(TgAuthEvent.ResendCode) },
            resendText = stringResource(R.string.larpgram_auth_code_resend),
            resendInText = { stringResource(R.string.larpgram_auth_code_resend_in, it) },
            resendAfterSeconds = state.resendAfterSeconds,
            codeSentCount = state.codeSentCount,
            isBusy = state.isBusy,
            length = TgAuthRules.CODE_LENGTH,
            error = state.error?.text(),
        )
    }
}

@Composable
private fun NewPasswordStep(state: TgAuthState) {
    val error = state.error
    val errorText = error?.text()
    fun submit() {
        if (state.canSubmitNewPassword) state.eventSink(TgAuthEvent.SubmitNewPassword)
    }
    FormStep(
        title = stringResource(R.string.larpgram_auth_new_password_title),
        subtitle = stringResource(R.string.larpgram_auth_new_password_subtitle),
    ) {
        TgFormPasswordField(
            value = state.newPassword,
            onValueChange = { state.eventSink(TgAuthEvent.SetNewPassword(it)) },
            label = stringResource(R.string.larpgram_auth_new_password_field),
            hint = stringResource(R.string.larpgram_auth_password_hint),
            error = errorText.takeIf { error?.kind in PASSWORD_ERRORS },
            enabled = !state.isBusy,
            isNewPassword = true,
        )
        Spacer(Modifier.height(16.dp))
        TgFormPasswordField(
            value = state.newPasswordRepeat,
            onValueChange = { state.eventSink(TgAuthEvent.SetNewPasswordRepeat(it)) },
            label = stringResource(R.string.larpgram_auth_password_repeat_field),
            error = errorText.takeIf { error?.kind == Kind.PasswordsDiffer },
            enabled = !state.isBusy,
            imeAction = ImeAction.Done,
            onDone = ::submit,
            isNewPassword = true,
        )
        StepError(errorText.takeIf { error?.kind !in FIELD_ERRORS })
        Spacer(Modifier.height(24.dp))
        TgFormPrimaryButton(
            text = stringResource(R.string.larpgram_auth_new_password_submit),
            onClick = ::submit,
            enabled = state.canSubmitNewPassword,
            showProgress = state.isBusy,
        )
    }
}

/**
 * Вход по QR-коду: это устройство — новое, код показывает уже вошедшее («Настройки» →
 * «Устройства» → «Привязать новое устройство»).
 */
@Composable
private fun ScanQrStep(state: TgAuthState) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    val deviceName = remember { "Larpgram Android (${Build.MANUFACTURER} ${Build.MODEL})" }

    FormStep(
        title = stringResource(R.string.larpgram_auth_qr_title),
        subtitle = stringResource(R.string.larpgram_auth_qr_subtitle),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ElementTheme.colors.bgSubtleSecondary),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    !hasCamera -> TgFormLink(
                        text = stringResource(R.string.larpgram_auth_qr_allow_camera),
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    )
                    state.isBusy -> CircularProgressIndicator(color = ElementTheme.colors.bgAccentRest)
                    else -> QrCodeCameraView(
                        modifier = Modifier.fillMaxSize(),
                        onScanQrCode = { bytes -> state.eventSink(TgAuthEvent.QrScanned(String(bytes, Charsets.UTF_8), deviceName)) },
                        // После ошибки ждём, пока человек сам попросит сканировать снова.
                        isScanning = state.error == null,
                    )
                }
            }
            StepError(state.error?.text())
            if (state.error != null) {
                TgFormLink(
                    text = stringResource(R.string.larpgram_auth_qr_retry),
                    onClick = { state.eventSink(TgAuthEvent.ClearError) },
                )
            }
        }
    }
}

private val USERNAME_ERRORS = arrayOf(Kind.UsernameTooShort, Kind.UsernameTooLong, Kind.UsernameInvalid, Kind.UsernameTaken)
private val EMAIL_ERRORS = arrayOf(Kind.EmailInvalid, Kind.EmailTaken, Kind.MailFailed)
private val PASSWORD_ERRORS = arrayOf(Kind.PasswordTooShort, Kind.PasswordTooLong, Kind.PasswordWeak)

/** Ошибки, которые рисуются под своим полем, а не общей строкой. */
private val FIELD_ERRORS = USERNAME_ERRORS + EMAIL_ERRORS + PASSWORD_ERRORS + Kind.PasswordsDiffer

@Composable
private fun TgAuthError.text(): String {
    val attempts = attemptsLeft
    if (kind == Kind.WrongCode && attempts != null) {
        return pluralStringResource(R.plurals.larpgram_auth_error_wrong_code_attempts, attempts, attempts)
    }
    return stringResource(
        when (kind) {
            Kind.InvalidCredentials -> R.string.larpgram_auth_error_invalid_credentials
            Kind.AccountDeactivated -> R.string.larpgram_auth_error_deactivated
            Kind.AlreadyLoggedIn -> R.string.larpgram_auth_error_already_logged_in
            Kind.UsernameTooShort -> R.string.larpgram_auth_error_username_too_short
            Kind.UsernameTooLong -> R.string.larpgram_auth_error_username_too_long
            Kind.UsernameInvalid -> R.string.larpgram_auth_error_username_invalid
            Kind.UsernameTaken -> R.string.larpgram_auth_error_username_taken
            Kind.EmailInvalid -> R.string.larpgram_auth_error_email_invalid
            Kind.EmailTaken -> R.string.larpgram_auth_error_email_taken
            Kind.PasswordTooShort -> R.string.larpgram_auth_error_password_too_short
            Kind.PasswordTooLong -> R.string.larpgram_auth_error_password_too_long
            Kind.PasswordWeak -> R.string.larpgram_auth_error_password_weak
            Kind.PasswordsDiffer -> R.string.larpgram_auth_error_passwords_differ
            Kind.WrongCode -> R.string.larpgram_auth_error_wrong_code
            Kind.CodeExpired -> R.string.larpgram_auth_error_code_expired
            Kind.TooManyAttempts -> R.string.larpgram_auth_error_too_many_attempts
            Kind.TooManyRequests -> R.string.larpgram_auth_error_too_many_requests
            Kind.MailFailed -> R.string.larpgram_auth_error_mail_failed
            Kind.QrInvalid -> R.string.larpgram_auth_error_qr_invalid
            Kind.QrExpired -> R.string.larpgram_auth_error_qr_expired
            Kind.Network -> R.string.larpgram_auth_error_network
        }
    )
}

@PreviewsDayNight
@Composable
internal fun TgAuthViewPreview(
    @PreviewParameter(TgAuthStatePreviewParam::class) state: TgAuthState,
) = ElementPreview {
    TgAuthView(
        state = state,
        onCloseClick = {},
        onReportProblemClick = {},
        onDeveloperSettingsClick = {},
    )
}
