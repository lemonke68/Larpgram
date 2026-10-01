/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.devices

import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.accountapi.api.AccountDevice
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.LoginOfferResult
import io.element.android.libraries.accountapi.api.LoginOfferState
import io.element.android.libraries.accountapi.test.FakeAccountSessionApi
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TgDevicesPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val thisDevice = AccountDevice("THIS", "Larpgram Android", 3_000, isCurrent = true)
    private val oldDevice = AccountDevice("OLD", "Old phone", 1_000, isCurrent = false)
    private val newDevice = AccountDevice("NEW", null, 2_000, isCurrent = false)

    @Test
    fun `devices load with this device first, then the most recent`() = runTest {
        val api = FakeAccountSessionApi(listDevicesLambda = { listOf(oldDevice, newDevice, thisDevice) })
        TgDevicesPresenter(api).test {
            val loaded = awaitState { it.devices != null }
            assertThat(loaded.devices).containsExactly(thisDevice, newDevice, oldDevice).inOrder()
            assertThat(loaded.loadFailed).isFalse()
        }
    }

    @Test
    fun `failed load can be retried`() = runTest {
        var answer: List<AccountDevice>? = null
        val api = FakeAccountSessionApi(listDevicesLambda = { answer })
        TgDevicesPresenter(api).test {
            val failed = awaitState { it.loadFailed }
            assertThat(failed.devices).isNull()
            answer = listOf(thisDevice)
            failed.eventSink(TgDevicesEvent.Refresh)
            assertThat(awaitState { it.devices != null }.loadFailed).isFalse()
        }
    }

    @Test
    fun `ending a session asks first and removes the device on success`() = runTest {
        val ended = mutableListOf<String>()
        val api = FakeAccountSessionApi(
            listDevicesLambda = { listOf(thisDevice, oldDevice) },
            endSessionLambda = {
                ended += it
                true
            },
        )
        TgDevicesPresenter(api).test {
            val sink = awaitState { it.devices != null }.eventSink
            sink(TgDevicesEvent.AskEnd(oldDevice))
            assertThat(awaitState { it.deviceToEnd != null }.deviceToEnd).isEqualTo(oldDevice)
            assertThat(ended).isEmpty()
            sink(TgDevicesEvent.ConfirmEnd)
            val done = awaitState { it.devices?.size == 1 && it.endingDeviceId == null }
            assertThat(done.devices).containsExactly(thisDevice)
            assertThat(ended).containsExactly("OLD")
        }
    }

    @Test
    fun `failed ending keeps the device and reports it`() = runTest {
        val api = FakeAccountSessionApi(listDevicesLambda = { listOf(thisDevice, oldDevice) }, endSessionLambda = { false })
        TgDevicesPresenter(api).test {
            val sink = awaitState { it.devices != null }.eventSink
            sink(TgDevicesEvent.AskEnd(oldDevice))
            sink(TgDevicesEvent.ConfirmEnd)
            val failed = awaitState { it.endFailed && it.endingDeviceId == null }
            assertThat(failed.devices).containsExactly(thisDevice, oldDevice)
        }
    }

    @Test
    fun `link shows a QR code and reports the linked device`() = runTest {
        var offerState: LoginOfferState? = LoginOfferState.Waiting
        var devices = listOf(thisDevice)
        val api = FakeAccountSessionApi(
            listDevicesLambda = { devices },
            createLoginOfferLambda = { LoginOfferResult.Offer("CODE", 120) },
            loginOfferStateLambda = { offerState },
        )
        TgDevicesPresenter(api).test {
            val sink = awaitState { it.devices != null }.eventSink
            sink(TgDevicesEvent.OpenLink)
            assertThat(awaitState { it.link is TgDeviceLink.Showing }.link).isEqualTo(TgDeviceLink.Showing("larpgram-login:CODE"))
            devices = listOf(thisDevice, newDevice)
            offerState = LoginOfferState.Redeemed
            awaitState { it.link == TgDeviceLink.Linked }
            sink(TgDevicesEvent.CloseLink)
            assertThat(awaitState { it.link == TgDeviceLink.Closed && it.devices?.size == 2 }.devices).containsExactly(thisDevice, newDevice)
        }
    }

    @Test
    fun `expired code is replaced by a new one, a failed offer is reported`() = runTest {
        val codes = mutableListOf("FIRST", "SECOND")
        val api = FakeAccountSessionApi(
            listDevicesLambda = { listOf(thisDevice) },
            createLoginOfferLambda = {
                if (codes.isEmpty()) LoginOfferResult.Failure(AccountError.Network) else LoginOfferResult.Offer(codes.removeAt(0), 120)
            },
            loginOfferStateLambda = { LoginOfferState.Expired },
        )
        TgDevicesPresenter(api).test {
            val sink = awaitState { it.devices != null }.eventSink
            sink(TgDevicesEvent.OpenLink)
            awaitState { it.link == TgDeviceLink.Showing("larpgram-login:FIRST") }
            awaitState { it.link == TgDeviceLink.Showing("larpgram-login:SECOND") }
            awaitState { it.link == TgDeviceLink.Failed }
        }
    }

    private suspend fun ReceiveTurbine<TgDevicesState>.awaitState(predicate: (TgDevicesState) -> Boolean): TgDevicesState =
        consumeItemsUntilPredicate(predicate = predicate).last().also { assertThat(predicate(it)).isTrue() }
}
