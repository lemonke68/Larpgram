/*
 * Модуль форка: проверка обновлений приложения (раздача APK мимо магазина).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.appupdate.api

/**
 * Знает, вышла ли версия свежее установленной.
 *
 * Larpgram раздаётся файлом, а не через Play Store, поэтому магазин за нас не уследит.
 * Клиент сам смотрит манифест на нашем сайте и, если там версия новее, предлагает
 * обновиться. Без этого люди застрянут на первой версии навсегда.
 */
interface UpdateChecker {
    /**
     * Сходить за манифестом и сравнить с установленной версией.
     *
     * Возвращает [UpdateStatus.Available] только когда версия и правда новее И её ещё не
     * отклоняли (см. [dismiss]). Всё остальное (актуальны, нет сети, мусор в манифесте) это
     * [UpdateStatus.UpToDate]/[UpdateStatus.Unknown] и повод баннер не показывать.
     */
    suspend fun check(): UpdateStatus

    /**
     * Запомнить, что обновление до этой версии отклонили. Баннер больше не покажется для
     * неё, но покажется для следующей, ещё более свежей версии.
     */
    suspend fun dismiss(versionCode: Long)
}

sealed interface UpdateStatus {
    /** Установлена последняя версия (или свежую уже отклонили). */
    data object UpToDate : UpdateStatus

    /** Выяснить не удалось: нет сети, сервер ответил ошибкой или манифест кривой. */
    data object Unknown : UpdateStatus

    /** Есть версия новее установленной, и её ещё не отклоняли. */
    data class Available(
        val versionName: String,
        val versionCode: Long,
    ) : UpdateStatus
}
