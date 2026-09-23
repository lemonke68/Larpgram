/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.dateformatter.impl

import android.os.Build
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.time.Instant

// Правка форка: в русском месяц после числа должен стоять в родительном падеже
// («14 сентября»), а не в именительном («14 сентябрь»). Сам баг живёт в desugared java.time и
// под Robolectric не воспроизводится — тест фиксирует ожидаемый вывод ICU-форматтеров.
@Config(qualifiers = "ru", sdk = [Build.VERSION_CODES.TIRAMISU])
class DefaultDateFormatterRuTest : RobolectricTest() {
    @Test
    fun `day separator in current year uses genitive month`() {
        val now = "2026-09-23T18:00:00.00Z"
        val ts = Instant.parse("2026-09-14T11:46:00.00Z").toEpochMilliseconds()
        val formatter = createFormatter(now)
        assertThat(formatter.format(ts, DateFormatterMode.Day, true)).isEqualTo("Понедельник, 14 сентября")
        assertThat(formatter.format(ts, DateFormatterMode.Day, false)).isEqualTo("Понедельник, 14 сентября")
    }

    @Test
    fun `day separator in previous year uses genitive month`() {
        val now = "2026-09-23T18:00:00.00Z"
        val ts = Instant.parse("2025-09-14T11:46:00.00Z").toEpochMilliseconds()
        val formatter = createFormatter(now)
        assertThat(formatter.format(ts, DateFormatterMode.Day, true)).isEqualTo("14 сентября 2025 г.")
    }

    @Test
    fun `month header keeps nominative month`() {
        val now = "2026-09-23T18:00:00.00Z"
        val ts = Instant.parse("2025-09-14T11:46:00.00Z").toEpochMilliseconds()
        val formatter = createFormatter(now)
        assertThat(formatter.format(ts, DateFormatterMode.Month)).isEqualTo("Сентябрь 2025 г.")
    }
}
