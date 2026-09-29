/*
 * Модуль форка: удаление чата и очистка истории, как в Telegram.
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
    namespace = "io.element.android.libraries.chatcleanup.impl"
}

setupDependencyInjection()

dependencies {
    api(projects.libraries.chatcleanup.api)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(projects.libraries.core)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.di)
    implementation(projects.libraries.keyescrow.api)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.uiStrings)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.keyescrow.test)
    testImplementation(projects.libraries.matrix.test)
}
