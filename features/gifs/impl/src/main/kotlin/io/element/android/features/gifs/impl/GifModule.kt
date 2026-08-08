/*
 * Модуль форка: гифки через свой прокси к Tenor.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.gifs.impl

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.CacheDirectory
import okhttp3.OkHttpClient
import java.io.File

/**
 * Repository и sender собираются здесь, а не аннотацией на классе: у обоих есть
 * параметры, которые графу взять неоткуда (адрес прокси и каталог кеша).
 */
@BindingContainer
@ContributesTo(AppScope::class)
object GifModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providesGifRepository(
        okHttpClient: OkHttpClient,
        coroutineDispatchers: CoroutineDispatchers,
    ): GifRepository = GifRepository(
        okHttpClient = okHttpClient,
        coroutineDispatchers = coroutineDispatchers,
    )

    @Provides
    @SingleIn(AppScope::class)
    fun providesRecentGifsStore(
        @ApplicationContext context: Context,
    ): RecentGifsStore = RecentGifsStore(context)

    @Provides
    @SingleIn(AppScope::class)
    fun providesGifSender(
        okHttpClient: OkHttpClient,
        coroutineDispatchers: CoroutineDispatchers,
        @CacheDirectory cacheDirectory: File,
    ): GifSender = GifSender(
        okHttpClient = okHttpClient,
        coroutineDispatchers = coroutineDispatchers,
        cacheDir = cacheDirectory,
    )
}
