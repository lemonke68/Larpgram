/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.tests.konsist

import com.google.common.truth.Truth.assertThat
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class KonsistLicenseTest {
    // Правка форка: новые файлы форка несут «Copyright (c) 2026 Larpgram.» вместо Element (аудит C-014).
    private val publicLicense = """
        /\*
        (?:.*\n)* \* Copyright \(c\) 20\d\d((, |-)20\d\d)? (Element Creations Ltd|Larpgram)\.
        (?:.*\n)* \*
         \* SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial\.
         \* Please see LICENSE files in the repository root for full details\.
         \*/
        """.trimIndent().toRegex()

    @Test
    fun `assert that FOSS files have the correct license header`() {
        Konsist
            .scopeFromProject()
            .files
            .filter {
                it.moduleName.startsWith("enterprise").not() &&
                    it.moduleName != "libraries/rustls-tls" &&
                    it.nameWithExtension != "locales.kt" &&
                    it.name.startsWith("Template ").not()
            }
            .also {
                assertThat(it).isNotEmpty()
            }
            .assertTrue {
                publicLicense.containsMatchIn(it.text)
            }
    }

    @Test
    fun `assert that files do not have double license header`() {
        Konsist
            .scopeFromProject()
            .files
            .filter {
                it.moduleName.endsWith("rustls-tls").not() &&
                it.nameWithExtension != "locales.kt" &&
                it.nameWithExtension != "KonsistLicenseTest.kt" &&
                    it.name.startsWith("Template ").not()
            }
            .assertTrue {
                // Правка форка: ровно один владелец в заголовке — Element или Larpgram.
                it.text.count("Element Creations Ltd.") + larpgramCopyright.findAll(it.text).count() == 1
            }
    }
}

private val larpgramCopyright = """ \* Copyright \(c\) 20\d\d Larpgram\.""".toRegex()

private fun String.count(subString: String): Int {
    var count = 0
    var index = 0
    while (true) {
        index = indexOf(subString, index)
        if (index == -1) return count
        count++
        index += subString.length
    }
}
