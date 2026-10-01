/*
 * Модуль форка: регистрация и сброс пароля из приложения (сервис server/account).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.accountapi.test"
}

dependencies {
    api(projects.libraries.accountapi.api)
    implementation(libs.coroutines.core)
}
