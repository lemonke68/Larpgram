/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: проверка обновлений приложения (раздача APK мимо магазина).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.appupdate.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.appupdate.api.UpdateStatus
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import java.io.IOException

/**
 * Сравнение версий по versionCode, отклонённая версия из account data, debug-манифест.
 * Манифест отдаёт перехватчик OkHttp, сети нет.
 */
class DefaultUpdateCheckerTest {
    private val manifest = """
        {"versionCode": 306, "versionName": "0.3.6", "apkUrl": "https://example.org/a.apk", "sha256": "ABCDEF", "extra": 1}
    """.trimIndent()

    @Test
    fun `newer versionCode is available with a lowercase sha256`() = runTest {
        val status = checker(installedCode = 305).check()
        assertThat(status).isEqualTo(
            UpdateStatus.Available(
                versionName = "0.3.6",
                versionCode = 306,
                apkUrl = "https://example.org/a.apk",
                sha256 = "abcdef",
            )
        )
    }

    @Test
    fun `same or newer installed build is up to date`() = runTest {
        assertThat(checker(installedCode = 306).check()).isEqualTo(UpdateStatus.UpToDate)
        assertThat(checker(installedCode = 400).check()).isEqualTo(UpdateStatus.UpToDate)
    }

    @Test
    fun `dismissed version and older stay quiet, a newer one shows again`() = runTest {
        val dismissed306 = FakeMatrixClient(getAccountDataLambda = { Result.success("""{"version_code":306}""") })
        assertThat(checker(installedCode = 305, matrixClient = dismissed306).check()).isEqualTo(UpdateStatus.UpToDate)
        val dismissed305 = FakeMatrixClient(getAccountDataLambda = { Result.success("""{"version_code":305}""") })
        assertThat(checker(installedCode = 300, matrixClient = dismissed305).check()).isInstanceOf(UpdateStatus.Available::class.java)
    }

    @Test
    fun `garbage in account data does not hide updates`() = runTest {
        val garbage = FakeMatrixClient(getAccountDataLambda = { Result.success("not json") })
        assertThat(checker(installedCode = 305, matrixClient = garbage).check()).isInstanceOf(UpdateStatus.Available::class.java)
    }

    @Test
    fun `missing apkUrl and sha256 fall back to the site apk and no hash`() = runTest {
        val status = checker(installedCode = 1, body = """{"versionCode": 2, "versionName": "x"}""").check()
        status as UpdateStatus.Available
        assertThat(status.apkUrl).endsWith("/larpgram.apk")
        assertThat(status.sha256).isNull()
    }

    @Test
    fun `network error, http error and bad json are unknown`() = runTest {
        assertThat(checker(installedCode = 1, failing = true).check()).isEqualTo(UpdateStatus.Unknown)
        assertThat(checker(installedCode = 1, code = 404).check()).isEqualTo(UpdateStatus.Unknown)
        assertThat(checker(installedCode = 1, body = "<html>").check()).isEqualTo(UpdateStatus.Unknown)
    }

    @Test
    fun `debug build reads its own manifest`() = runTest {
        val requests = mutableListOf<Request>()
        checker(installedCode = 1, isDebuggable = true, requests = requests).check()
        checker(installedCode = 1, isDebuggable = false, requests = requests).check()
        assertThat(requests.map { it.url.encodedPath }).containsExactly("/latest-debug.json", "/latest.json").inOrder()
    }

    @Test
    fun `dismiss stores the version code in account data`() = runTest {
        var stored: Pair<String, String>? = null
        val client = FakeMatrixClient(
            setAccountDataLambda = { type, content ->
                stored = type to content
                Result.success(Unit)
            }
        )
        checker(installedCode = 1, matrixClient = client).dismiss(306)
        assertThat(stored).isEqualTo("ru.mangokokos.larpgram.update_dismissed" to """{"version_code":306}""")
    }

    private fun TestScope.checker(
        installedCode: Long,
        matrixClient: FakeMatrixClient = FakeMatrixClient(),
        body: String = manifest,
        code: Int = 200,
        failing: Boolean = false,
        isDebuggable: Boolean = false,
        requests: MutableList<Request> = mutableListOf(),
    ) = DefaultUpdateChecker(
        matrixClient = matrixClient,
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests += chain.request()
                if (failing) throw IOException("offline")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build(),
        buildMeta = aBuildMeta(versionCode = installedCode, isDebuggable = isDebuggable),
        coroutineDispatchers = testCoroutineDispatchers(),
    )
}
