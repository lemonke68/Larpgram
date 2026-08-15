/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

import config.AnalyticsConfig
import config.BuildTimeConfig
import config.PushProvidersConfig

object ModulesConfig {
    val pushProvidersConfig = PushProvidersConfig(
        includeFirebase = BuildTimeConfig.PUSH_CONFIG_INCLUDE_FIREBASE,
        includeUnifiedPush = BuildTimeConfig.PUSH_CONFIG_INCLUDE_UNIFIED_PUSH,
    )

    val analyticsConfig: AnalyticsConfig = if (isEnterpriseBuild) {
        // Is Posthog configuration available?
        val withPosthog = BuildTimeConfig.SERVICES_POSTHOG_APIKEY.isNullOrEmpty().not() &&
            BuildTimeConfig.SERVICES_POSTHOG_HOST.isNullOrEmpty().not()
        // Is Sentry configuration available?
        val withSentry = BuildTimeConfig.SERVICES_SENTRY_DSN.isNullOrEmpty().not()
        if (withPosthog || withSentry) {
            println("Analytics enabled with Posthog: $withPosthog, Sentry: $withSentry")
            AnalyticsConfig.Enabled(
                withPosthog = withPosthog,
                withSentry = withSentry,
            )
        } else {
            println("Analytics disabled")
            AnalyticsConfig.Disabled
        }
    } else {
        // Правка форка: аналитики у нас нет вообще. Апстрим здесь включал PostHog и
        // Sentry принудительно, хотя ключи пустые (проверено в собранном BuildConfig:
        // POSTHOG_APIKEY, POSTHOG_HOST и SENTRY_DSN — пустые строки). Толку от этого
        // ноль, а цена есть: при входе показывается экран согласия на сбор данных, и
        // ссылка на политику в нём ведёт на element.io — чужой документ, который к
        // нашему приложению отношения не имеет.
        //
        // Disabled подставляет noop-сервис, у которого `didAskUserConsentFlow` всегда
        // true, поэтому шаг с согласием сам выпадает из онбординга.
        println("Analytics disabled (Larpgram)")
        AnalyticsConfig.Disabled
    }
}
