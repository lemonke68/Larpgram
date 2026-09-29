/*
 * Модуль форка: удаление чата и очистка истории, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.chatcleanup.api"
}

dependencies {
    implementation(libs.coroutines.core)
    api(projects.libraries.matrix.api)
}
