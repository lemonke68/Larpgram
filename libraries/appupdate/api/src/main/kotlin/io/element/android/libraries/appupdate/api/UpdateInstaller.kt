/*
 * Модуль форка: установка обновления из приложения.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.appupdate.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Качает APK новой версии и отдаёт его системному установщику — без браузера и поиска файла
 * в загрузках. Решение юзера (2026-09-25): тап «Обновить» в баннере, дальше системное окно
 * «Обновить?» и перезапуск. Молча без подтверждения не ставим.
 */
interface UpdateInstaller {
    val state: StateFlow<UpdateInstallState>

    /** Начать загрузку и установку. Во время загрузки повторный вызов ничего не делает. */
    fun install(update: UpdateStatus.Available)
}

sealed interface UpdateInstallState {
    /** Ничего не происходит. */
    data object Idle : UpdateInstallState

    /** Идёт загрузка APK. [progress] от 0 до 1, null — размер неизвестен. */
    data class Downloading(val progress: Float?) : UpdateInstallState

    /** APK отдан системе, ждём, пока человек нажмёт «Обновить» в системном окне. */
    data object WaitingForConfirmation : UpdateInstallState

    /** У приложения нет права ставить APK: открыли настройки, после разрешения нажать ещё раз. */
    data object NeedsPermission : UpdateInstallState

    /** Загрузка или установка не удалась. */
    data object Failed : UpdateInstallState
}
