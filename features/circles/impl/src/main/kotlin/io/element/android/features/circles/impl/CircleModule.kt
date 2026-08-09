/*
 * Модуль форка: кружочки, круглые видеосообщения как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.circles.impl

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.CacheDirectory
import io.element.android.libraries.di.annotations.ApplicationContext
import java.io.File

/**
 * Рекордер и отправитель собираются здесь: у обоих есть параметры, которые графу взять
 * неоткуда (контекст приложения и каталог кеша).
 */
@BindingContainer
@ContributesTo(AppScope::class)
object CircleModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providesCircleRecorder(
        @ApplicationContext context: Context,
        @CacheDirectory cacheDirectory: File,
    ): CircleRecorder = CircleRecorder(
        context = context,
        cacheDir = cacheDirectory,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun providesCircleSender(
        coroutineDispatchers: CoroutineDispatchers,
    ): CircleSender = CircleSender(
        coroutineDispatchers = coroutineDispatchers,
    )
}
