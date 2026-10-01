/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.accountapi.test.FakeAccountSessionApi
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LarpgramLogoutTest {
    @Test
    fun `plain logout does not touch the account service`() = runTest {
        val ended = mutableListOf<String>()
        val calls = mutableListOf<Boolean>()
        val client = FakeMatrixClient().apply { logoutLambda = { ignoreSdkError, _ -> calls += ignoreSdkError } }
        client.logoutOrEndSession(FakeAccountSessionApi(endSessionLambda = {
            ended += it
            true
        }), ignoreSdkError = false)
        assertThat(calls).containsExactly(false)
        assertThat(ended).isEmpty()
    }

    @Test
    fun `failed logout ends the session through the service, then clears local data`() = runTest {
        val ended = mutableListOf<String>()
        val calls = mutableListOf<Boolean>()
        val client = FakeMatrixClient().apply {
            logoutLambda = { ignoreSdkError, _ ->
                calls += ignoreSdkError
                if (!ignoreSdkError) throw AN_EXCEPTION
            }
        }
        client.logoutOrEndSession(FakeAccountSessionApi(endSessionLambda = {
            ended += it
            true
        }), ignoreSdkError = false)
        assertThat(ended).containsExactly(client.deviceId.value)
        assertThat(calls).containsExactly(false, true).inOrder()
    }

    @Test
    fun `when the service cannot end the session the original failure is reported`() = runTest {
        val client = FakeMatrixClient().apply { logoutLambda = { _, _ -> throw AN_EXCEPTION } }
        val failure = runCatching {
            client.logoutOrEndSession(FakeAccountSessionApi(endSessionLambda = { false }), ignoreSdkError = false)
        }.exceptionOrNull()
        assertThat(failure).isEqualTo(AN_EXCEPTION)
    }
}
