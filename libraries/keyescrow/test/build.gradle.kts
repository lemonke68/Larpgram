/*
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.keyescrow.test"
}

dependencies {
    api(projects.libraries.keyescrow.api)
    implementation(libs.coroutines.core)
}
