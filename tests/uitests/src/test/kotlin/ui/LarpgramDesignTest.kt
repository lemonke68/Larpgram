/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package ui

import base.LarpgramPreviewProvider
import base.PaparazziPreviewRule
import base.ScreenshotTest
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * Renders the shortlist of previews used while reworking the design, so the result can be looked at
 * without a device. Run with:
 *
 *     ./gradlew :tests:uitests:recordPaparazziDebug --tests "ui.LarpgramDesignTest"
 */
@RunWith(TestParameterInjector::class)
class LarpgramDesignTest(
    @TestParameter(valuesProvider = LarpgramPreviewProvider::class)
    val preview: ComposablePreview<AndroidPreviewInfo>,
) {
    @get:Rule(order = 0)
    val layoutLibErrorFilterStatement = LayoutLibErrorFilterStatement()

    @get:Rule(order = 1)
    val paparazziRule = PaparazziPreviewRule.createFor(preview, locale = "en")

    @Test
    fun snapshot() {
        ScreenshotTest.runTest(paparazzi = paparazziRule, preview = preview, localeStr = "en")
    }
}
