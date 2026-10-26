/*
 * Модуль форка: фоновый музыкальный плеер (фаза 6).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
import extension.testCommonDependencies

plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "io.element.android.libraries.musicplayer.api"
}

dependencies {
    implementation(libs.coroutines.core)
    api(projects.libraries.matrix.api)

    testCommonDependencies(libs)
}
