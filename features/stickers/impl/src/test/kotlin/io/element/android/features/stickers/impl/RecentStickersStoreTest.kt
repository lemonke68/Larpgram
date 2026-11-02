/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.stickers.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class RecentStickersStoreTest : RobolectricTest() {
    @Test
    fun `recent stickers are ordered, deduplicated and isolated by account`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val first = RecentStickersStore(context, FakeMatrixClient(sessionId = SessionId("@a$id:example.org")))
        val second = RecentStickersStore(context, FakeMatrixClient(sessionId = SessionId("@b$id:example.org")))

        first.remember("mxc://example.org/one")
        first.remember("mxc://example.org/two")
        first.remember("mxc://example.org/one")

        assertThat(first.getRecent()).containsExactly("mxc://example.org/one", "mxc://example.org/two").inOrder()
        assertThat(second.getRecent()).isEmpty()
    }
}
