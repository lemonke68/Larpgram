/*
 * Модуль форка: регистрация и сброс пароля из приложения (сервис server/account).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
import extension.setupDependencyInjection
import extension.testCommonDependencies

plugins {
    id("io.element.android-library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.element.android.libraries.accountapi.impl"
}

setupDependencyInjection()

dependencies {
    // Правка форка: адреса сервера — LarpgramHosts.
    implementation(projects.appconfig)
    api(projects.libraries.accountapi.api)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.network)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
}
