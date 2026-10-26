/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.enterprise.impl

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.features.enterprise.api.BugReportUrl
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import io.element.android.libraries.matrix.test.A_HOMESERVER_URL
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeTemporaryMatrixClient
import io.element.android.libraries.matrix.test.FakeTemporaryMatrixClientFactory
import io.element.android.libraries.matrix.test.accountprovider.anAccountProviderGeneric
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class DefaultEnterpriseServiceTest {
    @Test
    fun `accountProviderAllowList contains only our homeserver`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        assertThat(defaultEnterpriseService.accountProviderAllowList())
            .containsExactly(AccountProvider.Generic(DefaultEnterpriseService.HOMESERVER_URL))
    }

    @Test
    fun `canConnectToAnyAccountProvider is false`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        assertThat(defaultEnterpriseService.canConnectToAnyAccountProvider()).isFalse()
    }

    @Test
    fun `isAllowedToConnectToAccountProvider is true only for our homeserver and its subdomains`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        suspend fun isAllowed(url: String) = defaultEnterpriseService.isAllowedToConnectToAccountProvider(AccountProvider.Generic(url))
        assertThat(isAllowed(DefaultEnterpriseService.HOMESERVER_URL)).isTrue()
        // Synapse отвечает на поддомене, туда уводит well-known.
        assertThat(isAllowed("https://matrix.mango-kokos.ru")).isTrue()
        // Порт и путь не должны сбивать разбор.
        assertThat(isAllowed("https://matrix.mango-kokos.ru:8448/_matrix")).isTrue()
        assertThat(
            defaultEnterpriseService.isAllowedToConnectToAccountProvider(
                AccountProvider.Managed(serverName = "mango-kokos.ru", baseUrl = "https://matrix.mango-kokos.ru", canUseServerName = false)
            )
        ).isTrue()
        assertThat(isAllowed(A_HOMESERVER_URL)).isFalse()
        assertThat(defaultEnterpriseService.isAllowedToConnectToAccountProvider(anAccountProviderGeneric())).isFalse()
        // Чужой домен, который лишь заканчивается похоже, пролезать не должен.
        assertThat(isAllowed("https://evilmango-kokos.ru")).isFalse()
    }

    @Test
    fun `isEnterpriseUser always return false`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        assertThat(defaultEnterpriseService.isEnterpriseUser(A_SESSION_ID)).isFalse()
    }

    @Test
    fun `semanticColorsFlow always emits the same value`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        defaultEnterpriseService.semanticColorsFlow(null).test {
            val initialState = awaitItem()
            assertThat(initialState).isEqualTo(SemanticColorsLightDark.default)
            awaitComplete()
        }
    }

    @Test
    fun `brandColorsFlow always emits null`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        defaultEnterpriseService.brandColorsFlow(null).test {
            val initialState = awaitItem()
            assertThat(initialState).isNull()
            awaitComplete()
        }
    }

    @Test
    fun `semanticColorsFlow always emits the same value for a session`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        defaultEnterpriseService.semanticColorsFlow(A_SESSION_ID).test {
            val initialState = awaitItem()
            assertThat(initialState).isEqualTo(SemanticColorsLightDark.default)
            awaitComplete()
        }
    }

    @Test
    fun `overrideBrandColor has no effect`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        defaultEnterpriseService.overrideBrandColor(A_SESSION_ID, "aColor")
    }

    @Test
    fun `firebasePushGateway points at our own Sygnal`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        // Апстрим по умолчанию шлёт пуши через matrix.org, нам нужен свой шлюз.
        assertThat(defaultEnterpriseService.firebasePushGateway())
            .isEqualTo(DefaultEnterpriseService.PUSH_GATEWAY_URL)
    }

    @Test
    fun `unifiedPushDefaultPushGateway points at our own Sygnal`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        assertThat(defaultEnterpriseService.unifiedPushDefaultPushGateway())
            .isEqualTo(DefaultEnterpriseService.PUSH_GATEWAY_URL)
    }

    @Test
    fun `bugReportUrlFlow only emits UseDefault`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        defaultEnterpriseService.bugReportUrlFlow(A_SESSION_ID).test {
            assertThat(awaitItem()).isEqualTo(BugReportUrl.UseDefault)
            awaitComplete()
        }
    }

    @Test
    fun `getNoisyNotificationChannelId returns null`() = runTest {
        val defaultEnterpriseService = createDefaultEnterpriseService()
        assertThat(defaultEnterpriseService.getNoisyNotificationChannelId(A_SESSION_ID)).isNull()
    }

    @Test
    fun `isElementProEnforced checks using a temporary client`() = runTest {
        val closeLambda = lambdaRecorder<Unit> {}
        val getUrlLambda = lambdaRecorder<String, Result<ByteArray>> {
            Result.success("""{"enforce_element_pro": true}""".toByteArray())
        }
        val client = FakeTemporaryMatrixClient(
            getUrlResult = getUrlLambda,
            closeLambda = closeLambda,
        )
        val defaultEnterpriseService = createDefaultEnterpriseService(client = client)
        assertThat(defaultEnterpriseService.isElementProEnforced(A_HOMESERVER_URL)).isTrue()

        // Verify that the temporary client was used to fetch the URL and then closed
        val expectedUrl = "${A_HOMESERVER_URL.ensureProtocol()}/.well-known/element/element.json"
        getUrlLambda.assertions().isCalledOnce().with(value(expectedUrl))
        closeLambda.assertions().isCalledOnce()
    }

    private fun TestScope.createDefaultEnterpriseService(
        client: FakeTemporaryMatrixClient = FakeTemporaryMatrixClient(),
        jsonProvider: JsonProvider = { Json }
    ) = DefaultEnterpriseService(
        temporaryMatrixClientFactory = FakeTemporaryMatrixClientFactory(createResult = { Result.success(client) }),
        jsonProvider = jsonProvider,
        dispatchers = testCoroutineDispatchers(),
    )
}
