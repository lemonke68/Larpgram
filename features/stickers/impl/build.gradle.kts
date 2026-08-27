/*
 * Модуль форка: пикер стикеров.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
import extension.setupDependencyInjection
import extension.testCommonDependencies

plugins {
    id("io.element.android-compose-library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.element.android.features.stickers.impl"
}

setupDependencyInjection()

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.channelcomments)
    implementation(projects.libraries.core)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.di)
    api(projects.libraries.imagepacks.api)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.matrixmedia.api)
    implementation(projects.libraries.matrixui)
    implementation(projects.libraries.network)
    implementation(projects.libraries.uiStrings)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
}
