/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.appupdate.impl

import android.content.Context
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.appupdate.api.UpdateInstallState
import io.element.android.libraries.appupdate.api.UpdateStatus
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultUpdateInstallerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val apkBytes = ByteArray(64 * 1024) { (it % 251).toByte() }
    private val apkSha256 = MessageDigest.getInstance("SHA-256").digest(apkBytes).joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() {
        server.start()
        shadowOf(context.packageManager).setCanRequestPackageInstalls(true)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `without the unknown sources permission the installer asks for it`() = runTest {
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        val installer = installer()

        installer.install(anUpdate())

        assertThat(installer.state.value).isEqualTo(UpdateInstallState.NeedsPermission)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a matching apk is written into an install session and waits for the user`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(apkBytes)))
        val installer = installer()

        installer.install(anUpdate(sha256 = apkSha256))
        advanceUntilIdle()

        assertThat(installer.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
        assertThat(context.packageManager.packageInstaller.allSessions).hasSize(1)
    }

    @Test
    fun `an apk with a different sha256 is refused before installing`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(apkBytes)))
        val installer = installer()

        installer.install(anUpdate(sha256 = "00".repeat(32)))
        advanceUntilIdle()

        assertThat(installer.state.value).isEqualTo(UpdateInstallState.Failed)
        assertThat(context.packageManager.packageInstaller.allSessions).isEmpty()
    }

    @Test
    fun `an http error fails the update`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val installer = installer()

        installer.install(anUpdate(sha256 = apkSha256))
        advanceUntilIdle()

        assertThat(installer.state.value).isEqualTo(UpdateInstallState.Failed)
    }

    @Test
    fun `the system dialog result drives the state`() = runTest {
        val installer = installer()

        UpdateInstallReceiver.onResult?.invoke(PackageInstaller.STATUS_FAILURE_ABORTED)
        assertThat(installer.state.value).isEqualTo(UpdateInstallState.Idle)
        UpdateInstallReceiver.onResult?.invoke(PackageInstaller.STATUS_FAILURE)
        assertThat(installer.state.value).isEqualTo(UpdateInstallState.Failed)
        UpdateInstallReceiver.onResult?.invoke(PackageInstaller.STATUS_PENDING_USER_ACTION)
        assertThat(installer.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
    }

    private fun TestScope.installer() = DefaultUpdateInstaller(
        context = context,
        appCoroutineScope = backgroundScope,
        dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
    )

    private fun anUpdate(sha256: String? = null) = UpdateStatus.Available(
        versionName = "0.3.6",
        versionCode = 202609072,
        apkUrl = server.url("/larpgram.apk").toString(),
        sha256 = sha256,
    )
}
