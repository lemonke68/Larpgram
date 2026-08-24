/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.element.android.features.verifysession.impl.outgoing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.freeletics.flowredux.compose.rememberStateAndDispatch
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.keyescrow.api.KeyEscrowService
import io.element.android.libraries.keyescrow.api.RedeemResult
import io.element.android.libraries.keyescrow.api.RequestCodeResult
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.api.verification.VerificationFlowState
import io.element.android.libraries.matrix.api.verification.VerificationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import io.element.android.features.verifysession.impl.outgoing.OutgoingVerificationStateMachine.Event as StateMachineEvent
import io.element.android.features.verifysession.impl.outgoing.OutgoingVerificationStateMachine.State as StateMachineState

@AssistedInject
class OutgoingVerificationPresenter(
    @Assisted private val showDeviceVerifiedScreen: Boolean,
    @Assisted private val verificationRequest: VerificationRequest.Outgoing,
    private val sessionVerificationService: SessionVerificationService,
    private val encryptionService: EncryptionService,
    // Правка форка: верификация сессии кодом с почты вместо второго устройства.
    private val keyEscrowService: KeyEscrowService,
) : Presenter<OutgoingVerificationState> {
    @AssistedFactory
    fun interface Factory {
        fun create(
            verificationRequest: VerificationRequest.Outgoing,
            showDeviceVerifiedScreen: Boolean,
        ): OutgoingVerificationPresenter
    }

    private val stateMachine = OutgoingVerificationStateMachine(
        sessionVerificationService = sessionVerificationService,
        encryptionService = encryptionService,
    )

    @Composable
    override fun present(): OutgoingVerificationState {
        val coroutineScope = rememberCoroutineScope()
        val stateAndDispatch = stateMachine.rememberStateAndDispatch()
        // Правка форка: под-флоу «подтвердить по почте» живёт рядом с SDK-машиной.
        var emailStep by remember { mutableStateOf<EmailVerifyStep>(EmailVerifyStep.Hidden) }

        val sessionVerifiedStatus by sessionVerificationService.sessionVerifiedStatus.collectAsState()
        val step by remember {
            derivedStateOf {
                when (verificationRequest) {
                    is VerificationRequest.Outgoing.CurrentSession -> {
                        when (sessionVerifiedStatus) {
                            SessionVerifiedStatus.Unknown -> OutgoingVerificationState.Step.Loading
                            SessionVerifiedStatus.NotVerified -> {
                                stateAndDispatch.state.value.toVerificationStep()
                            }
                            SessionVerifiedStatus.Verified -> {
                                if (stateAndDispatch.state.value != StateMachineState.Initial || showDeviceVerifiedScreen) {
                                    // The user has verified the session, we need to show the success screen
                                    OutgoingVerificationState.Step.Completed
                                } else {
                                    // Automatic verification, which can happen on freshly created account, in this case, skip the screen
                                    OutgoingVerificationState.Step.Exit
                                }
                            }
                        }
                    }
                    is VerificationRequest.Outgoing.User -> stateAndDispatch.state.value.toVerificationStep()
                }
            }
        }

        // Start this after observing state machine
        LaunchedEffect(Unit) {
            // Force reset, just in case the service was left in a broken state
            sessionVerificationService.reset(cancelAnyPendingVerificationAttempt = true)

            observeVerificationService()
        }

        fun handleEvent(event: OutgoingVerificationViewEvents) {
            Timber.d("Verification user action: ${event::class.simpleName}")
            val stateMachineEvent = when (event) {
                // Just relay the event to the state machine
                OutgoingVerificationViewEvents.RequestVerification -> StateMachineEvent.RequestVerification(verificationRequest)
                OutgoingVerificationViewEvents.StartSasVerification -> StateMachineEvent.StartSasVerification
                OutgoingVerificationViewEvents.ConfirmVerification -> StateMachineEvent.AcceptChallenge
                OutgoingVerificationViewEvents.DeclineVerification -> StateMachineEvent.DeclineChallenge
                OutgoingVerificationViewEvents.Cancel -> StateMachineEvent.Cancel
                OutgoingVerificationViewEvents.Reset -> StateMachineEvent.Reset
                // Правка форка: под-флоу почты обрабатываем локально, в SDK-машину не отдаём.
                OutgoingVerificationViewEvents.StartEmailVerification -> {
                    coroutineScope.requestEmailCode { emailStep = it }
                    null
                }
                is OutgoingVerificationViewEvents.SubmitEmailCode -> {
                    coroutineScope.submitEmailCode(event.code, emailStep) { emailStep = it }
                    null
                }
                OutgoingVerificationViewEvents.DismissEmailVerification -> {
                    emailStep = EmailVerifyStep.Hidden
                    null
                }
            }
            stateMachineEvent?.let { stateAndDispatch.dispatchAction(it) }
        }
        return OutgoingVerificationState(
            step = step,
            request = verificationRequest,
            emailStep = emailStep,
            eventSink = ::handleEvent,
        )
    }

    /** Правка форка: просим сервер прислать код на почту аккаунта. */
    private fun CoroutineScope.requestEmailCode(update: (EmailVerifyStep) -> Unit) {
        update(EmailVerifyStep.SendingCode)
        launch {
            val next = when (val result = keyEscrowService.requestCode()) {
                is RequestCodeResult.Sent -> EmailVerifyStep.EnterCode(
                    maskedEmail = result.maskedEmail,
                    submitting = false,
                    error = null,
                )
                RequestCodeResult.NoEmail -> EmailVerifyStep.Unavailable(EmailVerifyUnavailable.NoEmail)
                RequestCodeResult.RateLimited -> EmailVerifyStep.Unavailable(EmailVerifyUnavailable.RateLimited)
                RequestCodeResult.NetworkError -> EmailVerifyStep.Unavailable(EmailVerifyUnavailable.Network)
            }
            update(next)
        }
    }

    /**
     * Правка форка: проверяем код и, если верный, забираем ключ и зовём recover.
     * После успешного recover статус сессии сам станет Verified, а step уедет в
     * Completed/Exit, поэтому под-флоу просто прячем.
     */
    private fun CoroutineScope.submitEmailCode(
        code: String,
        current: EmailVerifyStep,
        update: (EmailVerifyStep) -> Unit,
    ) {
        val entering = current as? EmailVerifyStep.EnterCode ?: return
        update(entering.copy(submitting = true, error = null))
        launch {
            when (val result = keyEscrowService.redeemCode(code)) {
                is RedeemResult.Success -> encryptionService.recover(result.recoveryKey).fold(
                    onSuccess = { update(EmailVerifyStep.Hidden) },
                    onFailure = {
                        Timber.w(it, "recover по ключу из escrow не удался")
                        update(entering.copy(submitting = false, error = EmailVerifyError.RecoverFailed))
                    },
                )
                is RedeemResult.InvalidCode ->
                    update(entering.copy(submitting = false, error = EmailVerifyError.InvalidCode(result.attemptsLeft)))
                RedeemResult.Expired ->
                    update(entering.copy(submitting = false, error = EmailVerifyError.Expired))
                RedeemResult.TooManyAttempts ->
                    update(entering.copy(submitting = false, error = EmailVerifyError.TooManyAttempts))
                RedeemResult.NoStoredKey ->
                    update(EmailVerifyStep.Unavailable(EmailVerifyUnavailable.NoStoredKey))
                RedeemResult.NetworkError ->
                    update(entering.copy(submitting = false, error = EmailVerifyError.Network))
            }
        }
    }

    private fun StateMachineState?.toVerificationStep(): OutgoingVerificationState.Step =
        when (val machineState = this) {
            StateMachineState.Initial, null -> {
                OutgoingVerificationState.Step.Initial
            }
            is StateMachineState.RequestingVerification,
            is StateMachineState.StartingSasVerification,
            StateMachineState.SasVerificationStarted -> {
                OutgoingVerificationState.Step.AwaitingOtherDeviceResponse
            }

            StateMachineState.VerificationRequestAccepted -> {
                OutgoingVerificationState.Step.Ready
            }

            is StateMachineState.Canceled -> {
                OutgoingVerificationState.Step.Canceled
            }

            is StateMachineState.Verifying -> {
                val async = when (machineState) {
                    is StateMachineState.Verifying.Replying -> AsyncData.Loading()
                    else -> AsyncData.Uninitialized
                }
                OutgoingVerificationState.Step.Verifying(machineState.data, async)
            }

            StateMachineState.Completed -> {
                OutgoingVerificationState.Step.Completed
            }

            StateMachineState.Exit -> {
                OutgoingVerificationState.Step.Exit
            }
        }

    private fun CoroutineScope.observeVerificationService() {
        sessionVerificationService.verificationFlowState
            .onEach { Timber.d("Verification flow state: ${it::class.simpleName}") }
            .onEach { verificationAttemptState ->
                when (verificationAttemptState) {
                    VerificationFlowState.Initial -> stateMachine.dispatch(StateMachineEvent.Reset)
                    VerificationFlowState.DidAcceptVerificationRequest -> {
                        stateMachine.dispatch(StateMachineEvent.DidAcceptVerificationRequest)
                    }
                    VerificationFlowState.DidStartSasVerification -> {
                        stateMachine.dispatch(StateMachineEvent.DidStartSasVerification)
                    }
                    is VerificationFlowState.DidReceiveVerificationData -> {
                        stateMachine.dispatch(StateMachineEvent.DidReceiveChallenge(verificationAttemptState.data))
                    }
                    VerificationFlowState.DidFinish -> {
                        stateMachine.dispatch(StateMachineEvent.DidAcceptChallenge)
                    }
                    VerificationFlowState.DidCancel -> {
                        stateMachine.dispatch(StateMachineEvent.DidCancel)
                    }
                    VerificationFlowState.DidFail -> {
                        stateMachine.dispatch(StateMachineEvent.DidFail)
                    }
                }
            }
            .launchIn(this)
    }
}
