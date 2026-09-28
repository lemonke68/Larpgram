/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: импорт стикер-паков из Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.stickers.impl.import

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.imagepacks.api.ImagePackSource
import io.element.android.libraries.matrix.api.MatrixClient
import okhttp3.OkHttpClient

/**
 * Импортёр собирается здесь, а не аннотацией на классе: адрес сервиса графу взять неоткуда.
 */
@BindingContainer
@ContributesTo(SessionScope::class)
object StickerImportModule {
    @Provides
    fun providesStickerPackImporter(
        okHttpClient: OkHttpClient,
        coroutineDispatchers: CoroutineDispatchers,
        matrixClient: MatrixClient,
        imagePackSource: ImagePackSource,
    ): StickerPackImporter = StickerPackImporter(
        okHttpClient = okHttpClient,
        coroutineDispatchers = coroutineDispatchers,
        matrixClient = matrixClient,
        imagePackSource = imagePackSource,
    )
}
