/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl.ui

import androidx.compose.runtime.Immutable
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.musicplayer.api.MusicOrder
import io.element.android.libraries.musicplayer.api.MusicRepeatMode
import kotlinx.collections.immutable.ImmutableList

/** Шторка плеера (`AudioPlayerAlert` Telegram): очередь, текущий трек, управление. */
@Immutable
data class MusicSheetState(
    /** «Имя — плейлист» у музыки профиля, иначе null. */
    val title: String?,
    /** Очередь в порядке показа: от новых к старым. */
    val tracks: ImmutableList<MusicSheetTrack>,
    val current: MusicSheetTrack?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val order: MusicOrder,
    val repeatMode: MusicRepeatMode,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val profileButton: ProfileButton,
    val profileAction: AsyncAction<ProfileButton>,
    /** Своя «музыка профиля»: треки можно убирать из списка. */
    val canRemoveTracks: Boolean,
    val eventSink: (MusicSheetEvent) -> Unit,
)

@Immutable
data class MusicSheetTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val durationMs: Long?,
    val fileSize: Long?,
    val artworkPath: String?,
    val isCurrent: Boolean,
)

/** Большая кнопка внизу шторки. */
enum class ProfileButton { Hidden, Add, Remove }

sealed interface MusicSheetEvent {
    data class Play(val trackId: String) : MusicSheetEvent
    data object PlayPause : MusicSheetEvent
    data object Next : MusicSheetEvent
    data object Previous : MusicSheetEvent
    data class Seek(val positionMs: Long) : MusicSheetEvent
    data object CycleSpeed : MusicSheetEvent
    data object CycleOrder : MusicSheetEvent
    data object CycleRepeat : MusicSheetEvent
    data object ToggleProfile : MusicSheetEvent
    data class RemoveFromProfile(val trackId: String) : MusicSheetEvent
    data object Share : MusicSheetEvent
    data object ClearProfileAction : MusicSheetEvent
}
