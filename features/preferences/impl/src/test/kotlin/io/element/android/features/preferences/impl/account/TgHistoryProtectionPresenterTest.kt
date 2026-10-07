/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.preferences.impl.account.TgHistoryProtectionState.Error
import io.element.android.features.preferences.impl.account.TgHistoryProtectionState.Status
import io.element.android.libraries.accountapi.test.FakeAccountSessionApi
import io.element.android.libraries.keyescrow.api.HistoryProtectionStatus
import io.element.android.libraries.keyescrow.test.FakeRecoveryKeyAutoProvisioner
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TgHistoryProtectionPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `a wrong password never reaches the key`() = runTest {
        val locked = mutableListOf<String>()
        val provisioner = FakeRecoveryKeyAutoProvisioner(
            protectionStatusLambda = { HistoryProtectionStatus(lockedWithPassword = false, serverRecovery = false) },
            lockWithPasswordLambda = {
                locked += it
                Result.success(Unit)
            },
        )
        val api = FakeAccountSessionApi(checkPasswordLambda = { it == "right" })
        TgHistoryProtectionPresenter(provisioner, api).test {
            val sink = awaitState { it.status == Status(lockedWithPassword = false, serverRecovery = false) }.eventSink
            sink(TgHistoryProtectionEvent.SetPassword("wrong"))
            sink(TgHistoryProtectionEvent.SubmitPassword)
            awaitState { it.error == Error.WrongPassword && !it.isBusy }
            assertThat(locked).isEmpty()

            sink(TgHistoryProtectionEvent.SetPassword("right"))
            sink(TgHistoryProtectionEvent.SubmitPassword)
            val done = awaitState { it.password.isEmpty() && !it.isBusy && it.error == null }
            assertThat(done.error).isNull()
            assertThat(locked).containsExactly("right")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no connection to the password check is a network error`() = runTest {
        val provisioner = FakeRecoveryKeyAutoProvisioner(
            protectionStatusLambda = { HistoryProtectionStatus(lockedWithPassword = false, serverRecovery = false) },
        )
        TgHistoryProtectionPresenter(provisioner, FakeAccountSessionApi()).test {
            val sink = awaitState { it.status != null }.eventSink
            sink(TgHistoryProtectionEvent.SetPassword("pw"))
            sink(TgHistoryProtectionEvent.SubmitPassword)
            awaitState { it.error == Error.Network && !it.isBusy }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `show key without a copy on this device says so`() = runTest {
        val provisioner = FakeRecoveryKeyAutoProvisioner(
            protectionStatusLambda = { HistoryProtectionStatus(lockedWithPassword = true, serverRecovery = false) },
        )
        TgHistoryProtectionPresenter(provisioner, FakeAccountSessionApi()).test {
            val sink = awaitState { it.status != null }.eventSink
            sink(TgHistoryProtectionEvent.ShowKey)
            val state = awaitState { it.error == Error.NoKeyOnDevice }
            assertThat(state.recoveryKey).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `server recovery toggle goes to the provisioner and the status is reloaded`() = runTest {
        var serverRecovery = false
        val provisioner = FakeRecoveryKeyAutoProvisioner(
            protectionStatusLambda = { HistoryProtectionStatus(lockedWithPassword = true, serverRecovery = serverRecovery) },
            setServerRecoveryLambda = {
                serverRecovery = it
                true
            },
            recoveryKeyOnDeviceLambda = { "KEY" },
        )
        TgHistoryProtectionPresenter(provisioner, FakeAccountSessionApi()).test {
            val sink = awaitState { it.status?.serverRecovery == false }.eventSink
            sink(TgHistoryProtectionEvent.SetServerRecovery(true))
            awaitState { it.status?.serverRecovery == true }
            sink(TgHistoryProtectionEvent.ShowKey)
            awaitState { it.recoveryKey == "KEY" }
            sink(TgHistoryProtectionEvent.HideKey)
            awaitState { it.recoveryKey == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unreachable escrow shows a retry`() = runTest {
        TgHistoryProtectionPresenter(FakeRecoveryKeyAutoProvisioner(), FakeAccountSessionApi()).test {
            val state = awaitState { !it.isLoading }
            assertThat(state.status).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun <T : Any> ReceiveTurbine<T>.awaitState(predicate: (T) -> Boolean): T =
        consumeItemsUntilPredicate(predicate = predicate).last().also { assertThat(predicate(it)).isTrue() }
}
