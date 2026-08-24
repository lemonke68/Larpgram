/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.outgoing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.verifysession.impl.R
import io.element.android.features.verifysession.impl.outgoing.OutgoingVerificationState.Step
import io.element.android.features.verifysession.impl.ui.VerificationBottomMenu
import io.element.android.features.verifysession.impl.ui.VerificationContentVerifying
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.atomic.pages.HeaderFooterPage
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.InvisibleButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.verification.SessionVerificationData
import io.element.android.libraries.matrix.api.verification.VerificationRequest
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutgoingVerificationView(
    state: OutgoingVerificationState,
    onLearnMoreClick: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = state.step
    fun cancelOrResetFlow() {
        when (step) {
            is Step.Canceled -> state.eventSink(OutgoingVerificationViewEvents.Reset)
            Step.Initial -> onBack()
            Step.Completed -> onFinish()
            Step.Ready, is Step.AwaitingOtherDeviceResponse -> state.eventSink(OutgoingVerificationViewEvents.Cancel)
            is Step.Verifying -> {
                if (!step.state.isLoading()) {
                    state.eventSink(OutgoingVerificationViewEvents.DeclineVerification)
                }
            }
            else -> Unit
        }
    }

    BackHandler {
        cancelOrResetFlow()
    }

    if (step is Step.Loading) {
        // Just display a loader in this case, to avoid UI glitch.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        HeaderFooterPage(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        if (step !is Step.Completed) {
                            BackButton(onClick = ::cancelOrResetFlow)
                        }
                    },
                    colors = topAppBarColors(containerColor = Color.Transparent),
                )
            },
            header = {
                OutgoingVerificationHeader(step = step, request = state.request)
            },
            footer = {
                OutgoingVerificationBottomMenu(
                    state = state,
                    onCancelClick = ::cancelOrResetFlow,
                    onDoneClick = onFinish,
                )
            },
            isScrollable = true,
        ) {
            OutgoingVerificationContent(
                step = step,
                request = state.request,
                onLearnMoreClick = onLearnMoreClick,
            )
        }
        // Правка форка: под-флоу «подтвердить по почте» рисуется диалогами поверх экрана.
        EmailVerificationDialogs(
            emailStep = state.emailStep,
            eventSink = state.eventSink,
        )
    }
}

/**
 * Правка форка: диалоги под-флоу верификации по коду с почты (escrow).
 */
@Composable
private fun EmailVerificationDialogs(
    emailStep: EmailVerifyStep,
    eventSink: (OutgoingVerificationViewEvents) -> Unit,
) {
    when (emailStep) {
        EmailVerifyStep.Hidden -> Unit
        EmailVerifyStep.SendingCode -> ProgressDialog(text = "Отправляем код на почту…")
        is EmailVerifyStep.EnterCode -> {
            if (emailStep.submitting) {
                ProgressDialog(text = "Проверяем код…")
            } else {
                val subtitle = buildString {
                    append("Код отправлен на ")
                    append(emailStep.maskedEmail)
                    append(". Введите 6 цифр из письма.")
                    emailStep.error?.let {
                        append("\n\n")
                        append(emailVerifyErrorText(it))
                    }
                }
                TextFieldDialog(
                    title = "Вход по коду с почты",
                    content = subtitle,
                    value = "",
                    placeholder = "000000",
                    submitText = "Подтвердить",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    validation = { it != null && it.trim().length == CODE_LENGTH && it.trim().all(Char::isDigit) },
                    onSubmit = { eventSink(OutgoingVerificationViewEvents.SubmitEmailCode(it.trim())) },
                    onDismissRequest = { eventSink(OutgoingVerificationViewEvents.DismissEmailVerification) },
                )
            }
        }
        is EmailVerifyStep.Unavailable -> ErrorDialog(
            content = emailVerifyUnavailableText(emailStep.reason),
            onSubmit = { eventSink(OutgoingVerificationViewEvents.DismissEmailVerification) },
        )
    }
}

private const val CODE_LENGTH = 6

private fun emailVerifyErrorText(error: EmailVerifyError): String = when (error) {
    is EmailVerifyError.InvalidCode -> error.attemptsLeft?.let {
        "Неверный код. Осталось попыток: $it."
    } ?: "Неверный код."
    EmailVerifyError.Expired -> "Срок кода истёк. Запросите новый: закройте и нажмите «Подтвердить по почте» снова."
    EmailVerifyError.TooManyAttempts -> "Слишком много попыток. Запросите новый код: закройте и начните заново."
    EmailVerifyError.RecoverFailed -> "Код верный, но восстановить ключи не удалось. Попробуйте ещё раз."
    EmailVerifyError.Network -> "Нет связи с сервером. Проверьте интернет и попробуйте снова."
}

private fun emailVerifyUnavailableText(reason: EmailVerifyUnavailable): String = when (reason) {
    EmailVerifyUnavailable.NoEmail ->
        "К аккаунту не привязана почта, поэтому этот способ недоступен. Подтвердите на другом устройстве."
    EmailVerifyUnavailable.NoStoredKey ->
        "Для этого аккаунта на сервере нет ключа. Войдите на устройстве, где уже есть доступ, и включите резервную копию."
    EmailVerifyUnavailable.RateLimited ->
        "Код запрашивали слишком часто. Подождите немного и попробуйте снова."
    EmailVerifyUnavailable.Network ->
        "Нет связи с сервером. Проверьте интернет и попробуйте снова."
}

@Composable
private fun OutgoingVerificationHeader(step: Step, request: VerificationRequest.Outgoing) {
    val iconStyle = when (step) {
        Step.Loading -> error("Should not happen")
        Step.AwaitingOtherDeviceResponse,
        Step.Initial -> when (request) {
            is VerificationRequest.Outgoing.CurrentSession -> BigIcon.Style.Default(CompoundIcons.Devices())
            is VerificationRequest.Outgoing.User -> BigIcon.Style.Default(CompoundIcons.UserProfileSolid())
        }
        Step.Canceled -> BigIcon.Style.AlertSolid
        Step.Ready -> BigIcon.Style.Default(CompoundIcons.ReactionSolid())
        Step.Completed -> BigIcon.Style.SuccessSolid
        is Step.Verifying -> {
            BigIcon.Style.Default(CompoundIcons.ReactionSolid())
        }
        is Step.Exit -> return
    }
    val titleTextId = when (step) {
        Step.Loading -> error("Should not happen")
        Step.Initial -> when (request) {
            is VerificationRequest.Outgoing.CurrentSession -> R.string.screen_session_verification_use_another_device_title
            is VerificationRequest.Outgoing.User -> R.string.screen_session_verification_user_initiator_title
        }
        Step.AwaitingOtherDeviceResponse -> when (request) {
            is VerificationRequest.Outgoing.CurrentSession -> R.string.screen_session_verification_waiting_another_device_title
            is VerificationRequest.Outgoing.User -> R.string.screen_session_verification_waiting_other_user_title
        }
        Step.Canceled -> CommonStrings.common_verification_failed
        Step.Ready -> R.string.screen_session_verification_compare_emojis_title
        Step.Completed -> when (request) {
            is VerificationRequest.Outgoing.CurrentSession -> R.string.screen_session_verification_device_verified
            is VerificationRequest.Outgoing.User -> CommonStrings.common_verification_complete
        }
        is Step.Verifying -> when (step.data) {
            is SessionVerificationData.Decimals -> R.string.screen_session_verification_compare_numbers_title
            is SessionVerificationData.Emojis -> R.string.screen_session_verification_compare_emojis_title
        }
        is Step.Exit -> return
    }
    val subtitleTextId = when (step) {
        Step.Loading -> error("Should not happen")
        Step.Initial -> when (request) {
            is VerificationRequest.Outgoing.CurrentSession -> R.string.screen_session_verification_use_another_device_subtitle
            is VerificationRequest.Outgoing.User -> R.string.screen_session_verification_user_initiator_subtitle
        }
        Step.AwaitingOtherDeviceResponse -> R.string.screen_session_verification_waiting_subtitle
        Step.Canceled -> R.string.screen_session_verification_failed_subtitle
        Step.Ready -> R.string.screen_session_verification_ready_subtitle
        Step.Completed -> when (request) {
            is VerificationRequest.Outgoing.CurrentSession -> R.string.screen_identity_confirmed_subtitle
            is VerificationRequest.Outgoing.User -> R.string.screen_session_verification_complete_user_subtitle
        }
        is Step.Verifying -> when (step.data) {
            is SessionVerificationData.Decimals -> R.string.screen_session_verification_compare_numbers_subtitle
            is SessionVerificationData.Emojis -> when (request) {
                is VerificationRequest.Outgoing.CurrentSession -> R.string.screen_session_verification_compare_emojis_subtitle
                is VerificationRequest.Outgoing.User -> R.string.screen_session_verification_compare_emojis_user_subtitle
            }
        }
        is Step.Exit -> return
    }
    val timeLimitMessage = if (step.isTimeLimited) {
        stringResource(CommonStrings.a11y_session_verification_time_limited_action_required)
    } else {
        ""
    }
    IconTitleSubtitleMolecule(
        modifier = Modifier
            .padding(bottom = 16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = timeLimitMessage
                focused = true
            }
            .focusable(),
        iconStyle = iconStyle,
        title = stringResource(id = titleTextId),
        subTitle = stringResource(id = subtitleTextId),
    )
}

@Composable
private fun OutgoingVerificationContent(
    step: Step,
    request: VerificationRequest.Outgoing,
    onLearnMoreClick: () -> Unit,
) {
    when (step) {
        is Step.Initial -> when (request) {
            is VerificationRequest.Outgoing.CurrentSession -> Unit
            is VerificationRequest.Outgoing.User -> ContentInitial(onLearnMoreClick)
        }
        is Step.Verifying -> VerificationContentVerifying(step.data)
        else -> Unit
    }
}

@Composable
private fun ContentInitial(
    onLearnMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            modifier = Modifier
                .clickable { onLearnMoreClick() }
                .padding(vertical = 4.dp, horizontal = 16.dp)
                .semantics {
                    // Note: there is no Role.Link, so we use Role.Button for better accessibility support
                    role = Role.Button
                },
            text = stringResource(CommonStrings.action_learn_more),
            style = ElementTheme.typography.fontBodyLgMedium
        )
    }
}

@Composable
private fun OutgoingVerificationBottomMenu(
    state: OutgoingVerificationState,
    onCancelClick: () -> Unit,
    onDoneClick: () -> Unit,
) {
    val eventSink = state.eventSink
    when (val step = state.step) {
        Step.Loading -> error("Should not happen")
        is Step.AwaitingOtherDeviceResponse,
        is Step.Initial -> {
            VerificationBottomMenu {
                val isWaiting = step is Step.AwaitingOtherDeviceResponse
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(CommonStrings.action_start_verification),
                    enabled = !isWaiting,
                    showProgress = isWaiting,
                    onClick = { eventSink(OutgoingVerificationViewEvents.RequestVerification) },
                )
                // Правка форка: альтернатива второму устройству — подтвердить кодом с почты.
                // Только при верификации своей сессии и пока не ждём ответа устройства.
                if (!isWaiting && state.request is VerificationRequest.Outgoing.CurrentSession) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = "Подтвердить по почте",
                        onClick = { eventSink(OutgoingVerificationViewEvents.StartEmailVerification) },
                    )
                } else {
                    InvisibleButton()
                }
            }
        }
        is Step.Canceled -> {
            VerificationBottomMenu {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(CommonStrings.action_done),
                    onClick = onCancelClick,
                )
                InvisibleButton()
            }
        }
        is Step.Ready -> {
            VerificationBottomMenu {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(CommonStrings.action_start),
                    onClick = { eventSink(OutgoingVerificationViewEvents.StartSasVerification) },
                )
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(CommonStrings.action_cancel),
                    onClick = onCancelClick,
                )
            }
        }
        is Step.Verifying -> {
            val isVerifying = step.state.isLoading()
            VerificationBottomMenu {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.screen_session_verification_they_match),
                    enabled = !isVerifying,
                    showProgress = isVerifying,
                    onClick = {
                        eventSink(OutgoingVerificationViewEvents.ConfirmVerification)
                    },
                )
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.screen_session_verification_they_dont_match),
                    enabled = !isVerifying,
                    onClick = {
                        eventSink(OutgoingVerificationViewEvents.DeclineVerification)
                    },
                )
            }
        }
        is Step.Completed -> {
            VerificationBottomMenu {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(CommonStrings.action_done),
                    onClick = onDoneClick,
                )
                InvisibleButton()
            }
        }
        is Step.Exit -> Unit
    }
}

@PreviewsDayNight
@Composable
internal fun OutgoingVerificationViewPreview(@PreviewParameter(OutgoingVerificationStateProvider::class) state: OutgoingVerificationState) = ElementPreview {
    OutgoingVerificationView(
        state = state,
        onLearnMoreClick = {},
        onFinish = {},
        onBack = {},
    )
}
