/*
 * Правка форка: заглушки для зависимостей форка в тестах списка чатов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.roomlist

import io.element.android.libraries.accountemail.api.AccountEmailStatus
import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackSource

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
 * Заглушка для временного вывода паков в logcat из презентера. Уедет вместе с ним.
 */
class FakeImagePackSource : ImagePackSource {
    override suspend fun getUserPacks(): List<ImagePack> = emptyList()

    override suspend fun getEmoteRooms(): List<Pair<String, String>> = emptyList()

    override suspend fun getRoomPack(roomId: String, stateKey: String): ImagePack? = null

    override suspend fun getSavedPacks(): List<ImagePack> = emptyList()

    override suspend fun savePack(pack: ImagePack): Boolean = true

    override suspend fun removeSavedPack(slug: String): Boolean = true

    override suspend fun getAllPacks(): List<ImagePack> = emptyList()
}
