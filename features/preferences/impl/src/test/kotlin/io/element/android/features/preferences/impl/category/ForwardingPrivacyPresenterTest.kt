/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.category

import com.google.common.truth.Truth.assertThat
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.ui.forward.ForwardingPrivacy
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ForwardingPrivacyPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `save waits for server and blocks duplicate taps then invalidates visible forward privacy`() = runTest {
        val finish = CompletableDeferred<Unit>()
        var writes = 0
        val client = FakeMatrixClient().apply {
            getForwardingProfileLinkLambda = { Result.success(false) }
            setForwardingProfileLinkLambda = {
                writes++
                finish.await()
                Result.success(Unit)
            }
        }
        val privacy = ForwardingPrivacy(client)
        assertThat(privacy.allowed(client.sessionId)).isFalse()
        ForwardingPrivacyPresenter(privacy, SnackbarDispatcher()).test {
            assertThat(awaitItem().isBusy).isTrue()
            val ready = awaitItem()
            assertThat(ready.allowed).isFalse()
            ready.change(true)
            ready.change(true)
            val pending = awaitItem()
            assertThat(pending.isBusy).isTrue()
            assertThat(pending.allowed).isFalse()
            assertThat(writes).isEqualTo(1)
            finish.complete(Unit)
            val done = awaitItem()
            assertThat(done.isBusy).isFalse()
            assertThat(done.allowed).isTrue()
            assertThat(privacy.allowed(client.sessionId)).isTrue()
            assertThat(privacy.revision.value).isEqualTo(1)
        }
    }

    @Test
    fun `a rejected write keeps the server value and reports failure`() = runTest {
        val finish = CompletableDeferred<Unit>()
        val client = FakeMatrixClient().apply {
            getForwardingProfileLinkLambda = { Result.success(false) }
            setForwardingProfileLinkLambda = {
                finish.await()
                Result.failure(IllegalStateException("denied"))
            }
        }
        val privacy = ForwardingPrivacy(client)
        val snackbar = SnackbarDispatcher()
        ForwardingPrivacyPresenter(privacy, snackbar).test {
            awaitItem()
            awaitItem().change(true)
            assertThat(awaitItem().isBusy).isTrue()
            finish.complete(Unit)
            val done = awaitItem()
            assertThat(done.isBusy).isFalse()
            assertThat(done.allowed).isFalse()
            assertThat(privacy.revision.value).isEqualTo(0)
            assertThat(snackbar.snackbarMessage.first()?.messageResId).isEqualTo(R.string.larpgram_forwarding_privacy_error)
        }
    }
}
