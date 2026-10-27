/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.musicplayer.api.MusicPlayerUi
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.ProfileMusic
import io.element.android.libraries.musicplayer.api.ProfileMusicTrack
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow

/** Профили в памяти: добавление кладёт трек наверх своего списка. */
class FakeProfileMusic(
    private val ownUserId: UserId = UserId("@alice:server.org"),
    profiles: Map<UserId, List<ProfileMusicTrack>> = emptyMap(),
    var result: Result<Unit> = Result.success(Unit),
) : ProfileMusic {
    private val profiles = profiles.toMutableMap()
    override val ownTracks = MutableStateFlow<ImmutableList<ProfileMusicTrack>?>(profiles[ownUserId]?.toImmutableList())

    val added = mutableListOf<MusicTrack>()
    val removed = mutableListOf<MusicTrack>()

    override suspend fun tracks(userId: UserId): Result<List<ProfileMusicTrack>> = Result.success(profiles[userId].orEmpty())

    override suspend fun refreshOwn() {
        ownTracks.value = profiles[ownUserId].orEmpty().toImmutableList()
    }

    override fun isInOwnProfile(track: MusicTrack): Boolean = ownTracks.value.orEmpty().any { it.url == track.mediaKey }

    override suspend fun add(track: MusicTrack, title: String, artist: String?): Result<Unit> {
        added += track
        return result.onSuccess {
            val entry = ProfileMusicTrack(url = track.mediaKey, title = title, artist = artist, filename = track.filename)
            profiles[ownUserId] = listOf(entry) + profiles[ownUserId].orEmpty()
            refreshOwn()
        }
    }

    override suspend fun remove(track: MusicTrack): Result<Unit> {
        removed += track
        return result.onSuccess {
            profiles[ownUserId] = profiles[ownUserId].orEmpty().filterNot { it.url == track.mediaKey }
            refreshOwn()
        }
    }
}

/** Ничего не рисует, запоминает запуски плейлистов профиля. */
class FakeMusicPlayerUi : MusicPlayerUi {
    val playedProfiles = mutableListOf<Pair<UserId, List<ProfileMusicTrack>>>()

    @Composable
    override fun NowPlayingBar(modifier: Modifier) = Unit

    @Composable
    override fun PlayerSheet(onDismiss: () -> Unit) = Unit

    @Composable
    override fun ProfileMusicRow(userId: UserId, ownerName: String?, modifier: Modifier) = Unit

    override fun playProfile(userId: UserId, ownerName: String?, tracks: List<ProfileMusicTrack>) {
        playedProfiles += userId to tracks
    }
}

fun aProfileMusicTrack(
    url: String = "mxc://server.org/song",
    title: String = "Song",
    artist: String? = "Artist",
) = ProfileMusicTrack(url = url, title = title, artist = artist, durationMs = 180_000, mimeType = "audio/mpeg", size = 1_000, filename = "$title.mp3")
