/*
 * Модуль форка: фоновый музыкальный плеер (фаза 6).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "io.element.android.libraries.musicplayer.test"
}

dependencies {
    api(projects.libraries.musicplayer.api)
    implementation(projects.libraries.matrix.api)
    implementation(libs.coroutines.core)
}
