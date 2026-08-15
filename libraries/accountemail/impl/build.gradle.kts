/*
 * Модуль форка: почта аккаунта (проверка привязки и баннер-напоминание).
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
    namespace = "io.element.android.libraries.accountemail.impl"
}

setupDependencyInjection()

dependencies {
    api(projects.libraries.accountemail.api)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.network)

    testCommonDependencies(libs)
    testImplementation(libs.network.mockwebserver)
    testImplementation(projects.libraries.matrix.test)
}
