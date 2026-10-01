/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.appconfig.LarpgramHosts
import io.element.android.features.login.impl.screens.onboarding.OnBoardingLogoResIdProvider
import io.element.android.features.login.impl.screens.tg.TgAuthError.Kind
import io.element.android.features.rageshake.api.RageshakeFeatureAvailability
import io.element.android.libraries.accountapi.api.AccountApi
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.CheckResult
import io.element.android.libraries.accountapi.api.ConfirmResult
import io.element.android.libraries.accountapi.api.LoginQrCode
import io.element.android.libraries.accountapi.api.LoginStartResult
import io.element.android.libraries.accountapi.api.RedeemLoginResult
import io.element.android.libraries.accountapi.api.ResendResult
import io.element.android.libraries.accountapi.api.StartResult
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.matrix.api.auth.AuthErrorCode
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.errorCode
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.ui.utils.MultipleTapToUnlock
import kotlinx.coroutines.launch

/**
 * Вход, регистрация и сброс пароля одним экраном с шагами, как в Telegram. Браузера нет: вход —
 * Matrix-логин по паролю, регистрация и сброс — сервис account ([AccountApi]) с кодом на почту.
 *
 * Шаги живут в одном презентере, а не в отдельных узлах: у них общие поля (ник со входа
 * подставляется в сброс, пароль с регистрации нужен после кода) и общая заявка сервиса.
 */
@Inject
class TgAuthPresenter(
    private val authenticationService: MatrixAuthenticationService,
    private val accountApi: AccountApi,
    private val sessionStore: SessionStore,
    private val buildMeta: BuildMeta,
    private val rageshakeFeatureAvailability: RageshakeFeatureAvailability,
    private val onBoardingLogoResIdProvider: OnBoardingLogoResIdProvider,
) : Presenter<TgAuthState> {
    private val multipleTapToUnlock = MultipleTapToUnlock()

    @Composable
    override fun present(): TgAuthState {
        val scope = rememberCoroutineScope()

        var step by rememberSaveable { mutableStateOf(TgAuthStep.Loading) }
        var isAddingAccount by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (step == TgAuthStep.Loading) {
                isAddingAccount = sessionStore.numberOfSessions() > 0
                step = if (isAddingAccount) TgAuthStep.Login else TgAuthStep.Welcome
            }
        }

        var login by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var registerUsername by rememberSaveable { mutableStateOf("") }
        var registerEmail by rememberSaveable { mutableStateOf("") }
        var registerPassword by rememberSaveable { mutableStateOf("") }
        var registerPasswordRepeat by rememberSaveable { mutableStateOf("") }
        var forgotLogin by rememberSaveable { mutableStateOf("") }
        var code by rememberSaveable { mutableStateOf("") }
        var newPassword by rememberSaveable { mutableStateOf("") }
        var newPasswordRepeat by rememberSaveable { mutableStateOf("") }

        // Заявка сервиса: номер и для кого она (ник и почта регистрации).
        var ticket by rememberSaveable { mutableStateOf("") }
        var ticketOwner by rememberSaveable { mutableStateOf("") }
        // Вход с кодом: ник, под которым входить после кода, и куда ушло письмо.
        var loginUsername by rememberSaveable { mutableStateOf("") }
        var loginEmailHint by rememberSaveable { mutableStateOf("") }
        // Код по заявке уже принят сервером: при отказе по паролю второй раз его не спрашиваем.
        var codeAccepted by rememberSaveable { mutableStateOf(false) }
        var resendAfterSeconds by rememberSaveable { mutableIntStateOf(0) }
        var codeSentCount by rememberSaveable { mutableIntStateOf(0) }

        var isBusy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<TgAuthError?>(null) }

        val canReportBug by remember { rageshakeFeatureAvailability.isAvailable() }.collectAsState(false)
        var showReportBug by rememberSaveable { mutableStateOf(false) }
        val logoResId = remember { onBoardingLogoResIdProvider.get() }

        fun fail(kind: Kind, attemptsLeft: Int? = null) {
            error = TgAuthError(kind, attemptsLeft)
            isBusy = false
        }

        fun codeSent(resendAfter: Int) {
            code = ""
            resendAfterSeconds = resendAfter
            codeSentCount++
        }

        /** Matrix-логин. При успехе сессия появляется в хранилище, и корень сам уводит с экрана. */
        suspend fun signIn(user: String, pass: String): Boolean {
            val result = authenticationService.setHomeserver(LarpgramHosts.HOMESERVER_URL)
                .mapCatching { authenticationService.login(user, pass).getOrThrow() }
            result.onFailure { fail(it.toKind()) }
            return result.isSuccess
        }

        /** Аккаунт создан или пароль сменён, а войти не вышло: пусть входит руками, данные подставлены. */
        suspend fun signInAfter(user: String, pass: String) {
            if (!signIn(user, pass)) {
                login = user
                password = pass
                step = TgAuthStep.Login
            }
        }

        fun confirmRegistration(enteredCode: String) = scope.launch {
            isBusy = true
            error = null
            when (val result = accountApi.confirmRegistration(ticket, enteredCode, registerPassword)) {
                is ConfirmResult.Done -> signInAfter(result.username, registerPassword)
                is ConfirmResult.WrongCode -> {
                    code = ""
                    fail(Kind.WrongCode, result.attemptsLeft)
                }
                is ConfirmResult.Failure -> {
                    val kind = result.error.toKind()
                    when (result.error) {
                        AccountError.PasswordWeak, AccountError.PasswordTooShort, AccountError.PasswordTooLong -> {
                            // Код сервер принял, не устроил пароль: назад к полям, заявка ещё жива.
                            codeAccepted = true
                            step = TgAuthStep.Register
                        }
                        AccountError.CodeExpired, AccountError.TooManyAttempts,
                        AccountError.UsernameTaken, AccountError.EmailTaken -> {
                            codeAccepted = false
                            ticket = ""
                            step = TgAuthStep.Register
                        }
                        else -> code = ""
                    }
                    fail(kind)
                }
            }
        }

        fun submitLogin() = scope.launch {
            isBusy = true
            error = null
            val identifier = TgAuthRules.loginIdentifier(login)
            // Сервис проверяет пароль и шлёт код на почту аккаунта; сам вход — обычный Matrix-логин
            // после кода. У аккаунта без почты кода нет.
            when (val result = accountApi.startLogin(identifier, password)) {
                is LoginStartResult.NoCodeNeeded -> signIn(result.username, password)
                is LoginStartResult.CodeSent -> {
                    ticket = result.ticket
                    loginUsername = result.username
                    loginEmailHint = result.emailHint
                    codeSent(result.resendAfterSeconds)
                    step = TgAuthStep.LoginCode
                    isBusy = false
                }
                is LoginStartResult.Failure -> if (result.error == AccountError.Network) {
                    // Сервис недоступен: вход не должен ломаться вместе с ним. Подтверждение кодом и так
                    // живёт только в приложении (сам `/login` сервера открыт), поэтому входим напрямую.
                    val user = if (TgAuthRules.looksLikeEmail(identifier)) accountApi.usernameByEmail(identifier) ?: identifier else identifier
                    signIn(user, password)
                } else {
                    fail(result.error.toKind())
                }
            }
        }

        fun confirmLogin(enteredCode: String) = scope.launch {
            isBusy = true
            error = null
            when (val result = accountApi.confirmLogin(ticket, enteredCode)) {
                is ConfirmResult.Done -> if (!signIn(loginUsername, password)) step = TgAuthStep.Login
                is ConfirmResult.WrongCode -> {
                    code = ""
                    fail(Kind.WrongCode, result.attemptsLeft)
                }
                is ConfirmResult.Failure -> {
                    code = ""
                    if (result.error == AccountError.CodeExpired || result.error == AccountError.TooManyAttempts) {
                        step = TgAuthStep.Login
                    }
                    fail(result.error.toKind())
                }
            }
        }

        fun submitRegister() {
            val username = TgAuthRules.normalizeUsername(registerUsername)
            val email = registerEmail.trim()
            val problem = TgAuthRules.usernameProblem(username)
                ?: TgAuthRules.emailProblem(email)
                ?: TgAuthRules.passwordProblem(registerPassword, registerPasswordRepeat)
            if (problem != null) return fail(problem)

            val owner = "$username\n$email"
            if (codeAccepted && ticket.isNotEmpty() && ticketOwner == owner) {
                confirmRegistration("")
                return
            }
            scope.launch {
                isBusy = true
                error = null
                when (val result = accountApi.startRegistration(username, email)) {
                    is StartResult.Sent -> {
                        ticket = result.ticket
                        ticketOwner = owner
                        codeAccepted = false
                        codeSent(result.resendAfterSeconds)
                        step = TgAuthStep.RegisterCode
                        isBusy = false
                    }
                    is StartResult.Failure -> fail(result.error.toKind())
                }
            }
        }

        fun submitForgot() = scope.launch {
            isBusy = true
            error = null
            when (val result = accountApi.forgotPassword(forgotLogin.trim())) {
                is StartResult.Sent -> {
                    ticket = result.ticket
                    codeSent(result.resendAfterSeconds)
                    step = TgAuthStep.ForgotCode
                    isBusy = false
                }
                is StartResult.Failure -> fail(result.error.toKind())
            }
        }

        fun checkResetCode(enteredCode: String) = scope.launch {
            isBusy = true
            error = null
            when (val result = accountApi.checkCode(ticket, enteredCode)) {
                CheckResult.Ok -> {
                    newPassword = ""
                    newPasswordRepeat = ""
                    step = TgAuthStep.NewPassword
                    isBusy = false
                }
                is CheckResult.WrongCode -> {
                    code = ""
                    fail(Kind.WrongCode, result.attemptsLeft)
                }
                is CheckResult.Failure -> {
                    code = ""
                    if (result.error == AccountError.CodeExpired || result.error == AccountError.TooManyAttempts) {
                        step = TgAuthStep.Forgot
                    }
                    fail(result.error.toKind())
                }
            }
        }

        fun submitNewPassword() {
            val problem = TgAuthRules.passwordProblem(newPassword, newPasswordRepeat)
            if (problem != null) return fail(problem)
            scope.launch {
                isBusy = true
                error = null
                when (val result = accountApi.resetPassword(ticket, code, newPassword)) {
                    is ConfirmResult.Done -> signInAfter(result.username, newPassword)
                    is ConfirmResult.WrongCode -> {
                        step = TgAuthStep.ForgotCode
                        code = ""
                        fail(Kind.WrongCode, result.attemptsLeft)
                    }
                    is ConfirmResult.Failure -> {
                        if (result.error == AccountError.CodeExpired || result.error == AccountError.TooManyAttempts) {
                            step = TgAuthStep.Forgot
                        }
                        fail(result.error.toKind())
                    }
                }
            }
        }

        fun resendCode() = scope.launch {
            isBusy = true
            error = null
            when (val result = accountApi.resendCode(ticket)) {
                is ResendResult.Sent -> {
                    codeSent(result.resendAfterSeconds)
                    isBusy = false
                }
                is ResendResult.TooSoon -> {
                    codeSent(result.retryAfterSeconds)
                    isBusy = false
                }
                is ResendResult.Failure -> {
                    if (result.error == AccountError.CodeExpired) {
                        step = when (step) {
                            TgAuthStep.RegisterCode -> TgAuthStep.Register
                            TgAuthStep.LoginCode -> TgAuthStep.Login
                            else -> TgAuthStep.Forgot
                        }
                    }
                    fail(result.error.toKind())
                }
            }
        }

        fun redeemQr(text: String, deviceName: String) {
            val loginCode = LoginQrCode.decode(text) ?: return fail(Kind.QrInvalid)
            scope.launch {
                isBusy = true
                error = null
                when (val result = accountApi.redeemLoginCode(loginCode, deviceName)) {
                    is RedeemLoginResult.Session -> {
                        authenticationService.setHomeserver(LarpgramHosts.HOMESERVER_URL)
                            .mapCatching {
                                authenticationService.loginWithAccessToken(result.userId, result.deviceId, result.accessToken).getOrThrow()
                            }
                            .onFailure { fail(it.toKind()) }
                    }
                    is RedeemLoginResult.Failure -> fail(
                        if (result.error == AccountError.CodeExpired) Kind.QrExpired else result.error.toKind()
                    )
                }
            }
        }

        fun setCode(value: String) {
            if (isBusy) return
            code = value.filter { it.isDigit() }.take(TgAuthRules.CODE_LENGTH)
            error = null
            if (code.length == TgAuthRules.CODE_LENGTH) {
                when (step) {
                    TgAuthStep.RegisterCode -> confirmRegistration(code)
                    TgAuthStep.ForgotCode -> checkResetCode(code)
                    TgAuthStep.LoginCode -> confirmLogin(code)
                    else -> Unit
                }
            }
        }

        fun goBack() {
            if (isBusy) return
            error = null
            step = when (step) {
                TgAuthStep.Login -> if (isAddingAccount) TgAuthStep.Login else TgAuthStep.Welcome
                TgAuthStep.Register, TgAuthStep.Forgot, TgAuthStep.ScanQr, TgAuthStep.LoginCode -> TgAuthStep.Login
                TgAuthStep.RegisterCode -> TgAuthStep.Register
                TgAuthStep.ForgotCode, TgAuthStep.NewPassword -> TgAuthStep.Forgot
                TgAuthStep.Loading, TgAuthStep.Welcome -> step
            }
        }

        fun edit(change: () -> Unit) {
            change()
            error = null
        }

        fun handleEvent(event: TgAuthEvent) {
            when (event) {
                TgAuthEvent.Start -> step = TgAuthStep.Login
                TgAuthEvent.OpenRegister -> if (!isBusy) {
                    error = null
                    step = TgAuthStep.Register
                }
                TgAuthEvent.OpenForgot -> if (!isBusy) {
                    error = null
                    if (forgotLogin.isBlank()) forgotLogin = login
                    step = TgAuthStep.Forgot
                }
                TgAuthEvent.OpenQrScan -> if (!isBusy) {
                    error = null
                    step = TgAuthStep.ScanQr
                }
                is TgAuthEvent.QrScanned -> if (!isBusy && error == null) redeemQr(event.text, event.deviceName)
                TgAuthEvent.Back -> goBack()
                is TgAuthEvent.SetLogin -> edit { login = event.value }
                is TgAuthEvent.SetPassword -> edit { password = event.value }
                TgAuthEvent.SubmitLogin -> if (!isBusy) submitLogin()
                is TgAuthEvent.SetRegisterUsername -> edit { registerUsername = event.value }
                is TgAuthEvent.SetRegisterEmail -> edit { registerEmail = event.value }
                is TgAuthEvent.SetRegisterPassword -> edit { registerPassword = event.value }
                is TgAuthEvent.SetRegisterPasswordRepeat -> edit { registerPasswordRepeat = event.value }
                TgAuthEvent.SubmitRegister -> if (!isBusy) submitRegister()
                is TgAuthEvent.SetForgotLogin -> edit { forgotLogin = event.value }
                TgAuthEvent.SubmitForgot -> if (!isBusy) submitForgot()
                is TgAuthEvent.SetCode -> setCode(event.value)
                TgAuthEvent.ResendCode -> if (!isBusy) resendCode()
                is TgAuthEvent.SetNewPassword -> edit { newPassword = event.value }
                is TgAuthEvent.SetNewPasswordRepeat -> edit { newPasswordRepeat = event.value }
                TgAuthEvent.SubmitNewPassword -> if (!isBusy) submitNewPassword()
                TgAuthEvent.ClearError -> error = null
                TgAuthEvent.VersionClick -> if (canReportBug && multipleTapToUnlock.unlock(scope)) {
                    showReportBug = true
                }
            }
        }

        return TgAuthState(
            step = step,
            isAddingAccount = isAddingAccount,
            showDeveloperSettings = buildMeta.buildType != BuildType.RELEASE,
            canReportBug = canReportBug && showReportBug,
            version = buildMeta.versionName,
            logoResId = logoResId,
            applicationName = buildMeta.productionApplicationName,
            login = login,
            password = password,
            registerUsername = registerUsername,
            registerEmail = registerEmail,
            registerPassword = registerPassword,
            registerPasswordRepeat = registerPasswordRepeat,
            forgotLogin = forgotLogin,
            code = code,
            loginEmailHint = loginEmailHint,
            resendAfterSeconds = resendAfterSeconds,
            codeSentCount = codeSentCount,
            newPassword = newPassword,
            newPasswordRepeat = newPasswordRepeat,
            isBusy = isBusy,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}

private fun Throwable.toKind(): Kind {
    val authException = this as? AuthenticationException ?: return Kind.Network
    if (authException is AuthenticationException.AccountAlreadyLoggedIn) return Kind.AlreadyLoggedIn
    return when (authException.errorCode) {
        AuthErrorCode.FORBIDDEN -> Kind.InvalidCredentials
        AuthErrorCode.USER_DEACTIVATED -> Kind.AccountDeactivated
        AuthErrorCode.UNKNOWN -> Kind.Network
    }
}

private fun AccountError.toKind(): Kind = when (this) {
    AccountError.UsernameTooShort -> Kind.UsernameTooShort
    AccountError.UsernameTooLong -> Kind.UsernameTooLong
    AccountError.UsernameInvalid -> Kind.UsernameInvalid
    AccountError.UsernameTaken -> Kind.UsernameTaken
    AccountError.EmailInvalid -> Kind.EmailInvalid
    AccountError.EmailTaken -> Kind.EmailTaken
    AccountError.PasswordTooShort -> Kind.PasswordTooShort
    AccountError.PasswordTooLong -> Kind.PasswordTooLong
    AccountError.PasswordWeak -> Kind.PasswordWeak
    AccountError.CodeExpired -> Kind.CodeExpired
    AccountError.TooManyAttempts -> Kind.TooManyAttempts
    AccountError.TooManyRequests -> Kind.TooManyRequests
    AccountError.MailFailed -> Kind.MailFailed
    AccountError.Network -> Kind.Network
    AccountError.InvalidCredentials -> Kind.InvalidCredentials
}
