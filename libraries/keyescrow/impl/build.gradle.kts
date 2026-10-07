/*
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
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
    namespace = "io.element.android.libraries.keyescrow.impl"
}

setupDependencyInjection()

dependencies {
    // Правка форка: адреса сервера — LarpgramHosts.
    implementation(projects.appconfig)
    api(projects.libraries.keyescrow.api)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.network)
    // Правка форка: escrow вариант B — Argon2id, копия ключа под Android Keystore.
    implementation(libs.bouncycastle.prov)
    implementation(libs.androidx.datastore.preferences)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.cryptography.api)
    implementation(projects.libraries.preferences.api)
    implementation(projects.libraries.sessionStorage.api)

    testCommonDependencies(libs)
    testImplementation(libs.network.mockwebserver)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.libraries.keyescrow.test)
    testImplementation(projects.libraries.cryptography.test)
    testImplementation(projects.libraries.preferences.test)
    testImplementation(projects.libraries.sessionStorage.test)
}
