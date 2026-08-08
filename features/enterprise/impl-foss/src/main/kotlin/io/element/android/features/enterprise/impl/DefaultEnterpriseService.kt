/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.enterprise.impl

import androidx.compose.ui.graphics.Color
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.features.enterprise.api.BugReportUrl
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.wellknown.api.ElementWellKnown
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@ContributesBinding(AppScope::class)
class DefaultEnterpriseService : EnterpriseService {
    override val isEnterpriseBuild = false

    override suspend fun isEnterpriseUser(sessionId: SessionId) = false
    override suspend fun tweakMasUrl(url: String, homeserver: String) = url

    // Larpgram работает только со своим сервером: единственный элемент списка убирает
    // выбор сервера из онбординга, а isAllowedToConnectToHomeserver закрывает остальные.
    override fun defaultHomeserverList(): List<String> = listOf(HOMESERVER_URL)

    override suspend fun isAllowedToConnectToHomeserver(homeserverUrl: String): Boolean {
        val host = homeserverUrl
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore(':')
            .lowercase()
        // Синапс живёт на matrix.mango-kokos.ru, а server_name это mango-kokos.ru,
        // поэтому после discovery по well-known сюда приходит поддомен.
        return host == HOMESERVER_DOMAIN || host.endsWith(".$HOMESERVER_DOMAIN")
    }

    override suspend fun overrideBrandColor(sessionId: SessionId?, brandColor: String?) = Unit

    override fun brandColorsFlow(sessionId: SessionId?): Flow<Color?> {
        return flowOf(null)
    }

    override fun semanticColorsFlow(sessionId: SessionId?): Flow<SemanticColorsLightDark> {
        return flowOf(SemanticColorsLightDark.default)
    }

    // Свой Sygnal вместо matrix.org: через шлюз идут уведомления всех наших людей.
    override fun firebasePushGateway(): String = PUSH_GATEWAY_URL

    override fun unifiedPushDefaultPushGateway(): String = PUSH_GATEWAY_URL

    override fun bugReportUrlFlow(sessionId: SessionId?): Flow<BugReportUrl> {
        return flowOf(BugReportUrl.UseDefault)
    }

    override fun getNoisyNotificationChannelId(sessionId: SessionId): String? = null

    override fun overriddenElementWellKnown(): ElementWellKnown? = null

    companion object {
        const val HOMESERVER_DOMAIN = "mango-kokos.ru"
        const val HOMESERVER_URL = "https://$HOMESERVER_DOMAIN"
        const val PUSH_GATEWAY_URL = "https://push.$HOMESERVER_DOMAIN/_matrix/push/v1/notify"
    }
}
