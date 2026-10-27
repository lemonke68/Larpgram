/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.musicplayer.api.MusicPlayer
import io.element.android.libraries.musicplayer.api.MusicPlayerUi
import io.element.android.libraries.musicplayer.api.ProfileMusic
import io.element.android.libraries.musicplayer.api.ProfileMusicTrack
import io.element.android.libraries.musicplayer.api.displayArtist
import io.element.android.libraries.musicplayer.api.displayTitle
import io.element.android.libraries.musicplayer.api.profileMusicQueueId
import io.element.android.libraries.musicplayer.api.toMusicTrack
import io.element.android.libraries.musicplayer.impl.R

@ContributesBinding(SessionScope::class)
class DefaultMusicPlayerUi(
    @ApplicationContext private val context: Context,
    private val sessionId: SessionId,
    private val musicPlayer: MusicPlayer,
    private val sheetPresenter: MusicSheetPresenter,
    private val profileMusic: ProfileMusic,
) : MusicPlayerUi {
    @Composable
    override fun NowPlayingBar(modifier: Modifier) {
        val state by musicPlayer.state.collectAsState()
        val tags by musicPlayer.tags.collectAsState()
        var sheetShown by rememberSaveable { mutableStateOf(false) }
        val track = state.currentTrack
        AnimatedVisibility(
            visible = track != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = modifier,
        ) {
            // Пока полоса уезжает, трека уже нет — показываем последний.
            if (track == null) return@AnimatedVisibility
            val trackTags = tags[track.mediaKey]
            NowPlayingBar(
                state = NowPlayingBarState(
                    title = track.displayTitle(trackTags),
                    artist = track.displayArtist(trackTags),
                    isPlaying = state.isPlaying || state.isBuffering,
                    progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f,
                    speed = state.speed,
                ),
                onAction = { action ->
                    when (action) {
                        NowPlayingBarAction.PlayPause -> musicPlayer.playPause()
                        NowPlayingBarAction.Open -> sheetShown = true
                        NowPlayingBarAction.CycleSpeed -> musicPlayer.cycleSpeed()
                        NowPlayingBarAction.Close -> musicPlayer.stop()
                    }
                },
            )
        }
        if (sheetShown && track != null) {
            PlayerSheet(onDismiss = { sheetShown = false })
        }
    }

    @Composable
    override fun PlayerSheet(onDismiss: () -> Unit) {
        val state = sheetPresenter.present()
        if (state.current == null) {
            // Плеер закрыли (из уведомления) — шторке показывать нечего.
            LaunchedEffect(Unit) { onDismiss() }
            return
        }
        MusicSheet(state = state, onDismiss = onDismiss)
    }

    @Composable
    override fun ProfileMusicRow(userId: UserId, ownerName: String?, modifier: Modifier) {
        val isOwn = userId == sessionId
        val own by profileMusic.ownTracks.collectAsState()
        var others by remember(userId) { mutableStateOf<List<ProfileMusicTrack>>(emptyList()) }
        LaunchedEffect(userId) {
            // Свой список держит ProfileMusic (меняется после «Добавить в профиль»), чужой читаем тут.
            if (isOwn) {
                profileMusic.refreshOwn()
            } else {
                others = profileMusic.tracks(userId).getOrDefault(emptyList())
            }
        }
        val tracks = if (isOwn) own.orEmpty() else others
        var sheetShown by rememberSaveable { mutableStateOf(false) }
        val top = tracks.firstOrNull() ?: return
        ProfileMusicRowView(
            title = top.title,
            artist = top.artist,
            onClick = {
                playProfile(userId, ownerName, tracks)
                sheetShown = true
            },
            modifier = modifier,
        )
        if (sheetShown) {
            PlayerSheet(onDismiss = { sheetShown = false })
        }
    }

    override fun playProfile(userId: UserId, ownerName: String?, tracks: List<ProfileMusicTrack>) {
        if (tracks.isEmpty()) return
        val queueId = profileMusicQueueId(userId)
        val current = musicPlayer.state.value
        // Плейлист уже играет — просто открываем шторку, не начиная заново.
        if (current.queueId == queueId && current.isActive) return
        val musicTracks = tracks.map { it.toMusicTrack(sessionId) }
        val title = context.getString(R.string.larpgram_music_profile_playlist, ownerName ?: userId.value)
        musicPlayer.play(queueId, musicTracks, musicTracks.first().id, title)
    }
}
