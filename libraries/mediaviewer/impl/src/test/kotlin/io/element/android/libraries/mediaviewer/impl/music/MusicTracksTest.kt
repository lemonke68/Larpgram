/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.music

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.mediaviewer.api.anAudioMediaInfo
import io.element.android.libraries.mediaviewer.impl.model.MediaItem
import io.element.android.libraries.mediaviewer.impl.model.aMediaItemAudio
import io.element.android.libraries.mediaviewer.impl.model.aMediaItemFile
import io.element.android.libraries.mediaviewer.impl.model.aMediaItemVoice
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import io.element.android.libraries.musicplayer.test.aMusicTrack
import org.junit.Test

class MusicTracksTest {
    @Test
    fun `room audio becomes a newest first queue without voice messages`() {
        val newest = aMediaItemAudio(id = UniqueId("3"), eventId = EventId("\$3"))
        val audioAsFile = aMediaItemFile(id = UniqueId("2")).let {
            MediaItem.File(it.id, EventId("\$2"), anAudioMediaInfo(filename = "doc.flac").copy(mimeType = MimeTypes.Mp3), it.mediaSource, it.validationState)
        }
        val document = aMediaItemFile(id = UniqueId("pdf"))
        val oldest = aMediaItemAudio(id = UniqueId("1"), eventId = EventId("\$1"))
        val tracks = listOf(newest, aMediaItemVoice(), audioAsFile, document, oldest).toMusicTracks(A_SESSION_ID, A_ROOM_ID)
        assertThat(tracks.map { it.id }).containsExactly("\$3", "\$2", "\$1").inOrder()
        assertThat(tracks.first().roomId).isEqualTo(A_ROOM_ID)
        assertThat(tracks.first().sessionId).isEqualTo(A_SESSION_ID)
    }

    @Test
    fun `a local echo is keyed by its media url`() {
        val track = listOf(aMediaItemAudio(eventId = null)).toMusicTracks(A_SESSION_ID, A_ROOM_ID).single()
        assertThat(track.id).isEqualTo(track.mediaSource.safeUrl)
    }

    @Test
    fun `voice is not music`() {
        assertThat(aMediaItemVoice().mediaInfo.isMusic()).isFalse()
        assertThat(aMediaItemAudio().mediaInfo.isMusic()).isTrue()
    }

    @Test
    fun `search matches every word in title, artist, album or filename`() {
        val daft = aMusicTrack(id = "a", filename = "Daft Punk - Veridis Quo.flac")
        val tagged = aMusicTrack(id = "b", filename = "track02.mp3")
        val other = aMusicTrack(id = "c", filename = "Other - Song.mp3")
        val tracks = listOf(daft, tagged, other)
        val tags = mapOf(tagged.mediaKey to MusicTrackTags(title = "Around the World", artist = "Daft Punk", album = "Homework"))
        assertThat(tracks.filterMusic("", tags)).isEqualTo(tracks)
        assertThat(tracks.filterMusic("daft", tags)).containsExactly(daft, tagged).inOrder()
        assertThat(tracks.filterMusic("punk homework", tags)).containsExactly(tagged)
        assertThat(tracks.filterMusic("flac", tags)).containsExactly(daft)
        assertThat(tracks.filterMusic("nothing", tags)).isEmpty()
    }
}
