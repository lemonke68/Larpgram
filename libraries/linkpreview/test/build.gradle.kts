/*
 * Модуль форка: превью ссылок в сообщениях, как в Telegram (данные — preview_url Synapse).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.linkpreview.test"
}

dependencies {
    api(projects.libraries.linkpreview.api)
}
