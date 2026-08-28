/*
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.keyescrow.api"
}

dependencies {
    // Правка форка: RoomId для «удалить у обоих».
    api(projects.libraries.matrix.api)
}
