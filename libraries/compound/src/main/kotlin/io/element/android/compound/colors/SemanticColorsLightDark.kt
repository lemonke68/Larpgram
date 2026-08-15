/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.compound.colors

import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.larpgramColorsDark
import io.element.android.compound.tokens.larpgramColorsLight

data class SemanticColorsLightDark(
    val light: SemanticColors,
    val dark: SemanticColors,
) {
    companion object {
        /**
         * Правка форка: наша палитра, а не сырые токены Compound.
         *
         * Это второй вход для цветов, и именно он работает в живом приложении: MainActivity
         * берёт палитру отсюда через `EnterpriseService.semanticColorsFlow` и передаёт её в
         * `ElementTheme` явным параметром, перебивая значения по умолчанию. Правка одних
         * дефолтов в `ElementTheme` меняет только Compose-превью.
         */
        val default = SemanticColorsLightDark(
            light = larpgramColorsLight,
            dark = larpgramColorsDark,
        )
    }
}
