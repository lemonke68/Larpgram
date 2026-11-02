/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.musicplayer.impl.ui

import android.content.Context
import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.media.AudioInfo
import io.element.android.libraries.matrix.api.room.ForwardEventException
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.impl.MusicCopyPolicy
import io.element.android.libraries.musicplayer.impl.MusicTrackResolver
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/** Upload the original audio bytes, without forwarded attribution or lossy transcoding. */
@Inject
class MusicTrackSender(
    @ApplicationContext private val context: Context,
    private val client: MatrixClient,
    private val resolver: MusicTrackResolver,
    private val copyPolicy: MusicCopyPolicy,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun send(track: MusicTrack, roomIds: List<RoomId>, caption: String): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            if (roomIds.isEmpty()) return@runCatchingExceptions
            copyPolicy.requireAllowed(track)
            val directory = File(context.cacheDir, "temp/music-send/${UUID.randomUUID()}")
            val name = track.filename.replace('/', '_').takeUnless { it.isBlank() || it == "." || it == ".." } ?: "track"
            try {
                val file = resolver.copyForExport(track, File(directory, "source/$name"))
                // Once an upload starts, keep the source until the SDK has accepted every target,
                // even if the player/sheet disappears. SDK upload handlers copy asynchronously.
                withContext(NonCancellable) {
                    val failed = roomIds.distinct().filter { roomId ->
                        runCatchingExceptions {
                            copyPolicy.requireAllowed(track)
                            val room = client.getJoinedRoom(roomId) ?: error("Recipient is not joined")
                            room.use {
                                // MediaUploadHandler owns and deletes its input after await.
                                val recipientDirectory = File(directory, UUID.randomUUID().toString()).apply { mkdirs() }
                                val recipientFile = file.copyTo(File(recipientDirectory, file.name))
                                it.liveTimeline.sendAudio(
                                    file = recipientFile,
                                    audioInfo = AudioInfo(track.durationMs?.milliseconds, file.length(), track.mimeType),
                                    caption = caption.takeIf(String::isNotBlank),
                                    formattedCaption = null,
                                    inReplyToEventId = null,
                                ).getOrThrow().await().getOrThrow()
                            }
                        }.isFailure
                    }
                    if (failed.isNotEmpty()) throw ForwardEventException(failed)
                }
            } finally {
                directory.deleteRecursively()
            }
        }
    }
}
