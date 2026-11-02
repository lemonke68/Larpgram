/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.forward

import androidx.compose.runtime.staticCompositionLocalOf
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Coalesces profile reads for visible forwards. Export itself always checks fresh policy. */
@Inject
@SingleIn(SessionScope::class)
class ForwardingPrivacy(private val client: MatrixClient) {
    private val mutex = Mutex()
    private data class Cached(val allowed: Boolean, val readAt: TimeSource.Monotonic.ValueTimeMark)
    private val cache = mutableMapOf<UserId, Cached>()
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()

    suspend fun allowed(userId: UserId): Boolean = mutex.withLock {
        cache[userId]?.takeIf { it.readAt.elapsedNow() < 60.seconds }?.let { return@withLock it.allowed }
        val allowed = client.getForwardingProfileLink(userId).getOrDefault(false)
        cache[userId] = Cached(allowed, TimeSource.Monotonic.markNow())
        allowed
    }

    suspend fun ownPreference(): Result<Boolean> = client.getForwardingProfileLink(client.sessionId)

    suspend fun setOwnPreference(allowed: Boolean): Result<Unit> = client.setForwardingProfileLink(allowed).onSuccess {
        mutex.withLock { cache[client.sessionId] = Cached(allowed, TimeSource.Monotonic.markNow()) }
        _revision.value += 1
    }
}

val LocalForwardingPrivacy = staticCompositionLocalOf<ForwardingPrivacy?> { null }
