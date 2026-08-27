/*
 * Модуль форка: общая логика TG-стиля «канал ↔ комментарии» (ссылка на дискуссию, зеркалирование
 * постов в дискуссию). Вынесено сюда, чтобы и messages, и stickers (и прочие send-пути) могли звать
 * одно и то же зеркалирование без циклической зависимости между фичами.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.channelcomments"
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(projects.libraries.core)
    implementation(projects.libraries.matrix.api)
    implementation(libs.serialization.json)
}
