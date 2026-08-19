/*
 * Правка форка: заглушки для зависимостей форка в тестах списка чатов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.roomlist

import io.element.android.libraries.accountemail.api.AccountEmailStatus
import io.element.android.libraries.appupdate.api.UpdateChecker
import io.element.android.libraries.appupdate.api.UpdateStatus

/**
 * По умолчанию почта есть и баннер не нужен: в тестах про другое он только мешал бы.
 */
class FakeAccountEmailStatus(
    private val hasEmailResult: () -> Boolean? = { true },
    private val isBannerHiddenResult: () -> Boolean = { false },
    private val onHideBanner: () -> Unit = {},
) : AccountEmailStatus {
    override suspend fun hasEmail(): Boolean? = hasEmailResult()

    override suspend fun isBannerHidden(): Boolean = isBannerHiddenResult()

    override suspend fun hideBanner() = onHideBanner()
}

/**
 * По умолчанию обновлений нет: в тестах про другое баннер только мешал бы.
 */
class FakeUpdateChecker(
    private val checkResult: () -> UpdateStatus = { UpdateStatus.UpToDate },
    private val onDismiss: (Long) -> Unit = {},
) : UpdateChecker {
    override suspend fun check(): UpdateStatus = checkResult()

    override suspend fun dismiss(versionCode: Long) = onDismiss(versionCode)
}
