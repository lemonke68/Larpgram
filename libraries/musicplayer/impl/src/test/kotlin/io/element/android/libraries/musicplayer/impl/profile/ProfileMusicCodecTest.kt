/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl.profile

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.musicplayer.test.aMusicTrack
import io.element.android.libraries.musicplayer.test.aProfileMusicTrack
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class ProfileMusicCodecTest {
    @Test
    fun `a stored list round-trips through the profile field`() {
        val tracks = listOf(
            aProfileMusicTrack(url = "mxc://s/1", title = "One", artist = "A").copy(origin = "abc"),
            aProfileMusicTrack(url = "mxc://s/2", title = "Two", artist = null),
        )
        val body = ProfileMusicCodec.encodeBody(ProfileMusicCodec.encodeValue(tracks))
        assertThat(body).contains("\"ru.mangokokos.music\"")
        assertThat(body).contains("\"duration_ms\":180000")
        assertThat(body).contains("\"mimetype\":\"audio/mpeg\"")
        assertThat(ProfileMusicCodec.decodeResponse(body)).isEqualTo(tracks)
    }

    @Test
    fun `a value stored as a string is understood too`() {
        val tracks = listOf(aProfileMusicTrack())
        val asString = JsonPrimitive(ProfileMusicCodec.encodeValue(tracks).toString())
        assertThat(ProfileMusicCodec.decodeResponse(ProfileMusicCodec.encodeBody(asString))).isEqualTo(tracks)
    }

    @Test
    fun `broken entries and other fields are skipped`() {
        val body = """
            {"ru.mangokokos.music": [
              {"url": "https://evil", "title": "x"},
              {"url": "mxc://s/1", "title": ""},
              {"title": "no url"},
              42,
              {"url": "mxc://s/2", "title": "Ok", "extra": true}
            ], "displayname": "Alice"}
        """.trimIndent()
        val decoded = ProfileMusicCodec.decodeResponse(body)
        assertThat(decoded.map { it.url }).containsExactly("mxc://s/2")
        assertThat(decoded.single().filename).isEqualTo("Ok")
        assertThat(ProfileMusicCodec.decodeResponse("not json")).isEmpty()
        assertThat(ProfileMusicCodec.decodeResponse("{}")).isEmpty()
    }

    @Test
    fun `adding puts the track on top and replaces the same file or source`() {
        val current = listOf(
            aProfileMusicTrack(url = "mxc://s/1", title = "One").copy(origin = "o1"),
            aProfileMusicTrack(url = "mxc://s/2", title = "Two"),
        )
        val sameSource = aProfileMusicTrack(url = "mxc://s/9", title = "One again").copy(origin = "o1")
        assertThat(ProfileMusicCodec.withAdded(current, sameSource).map { it.url }).containsExactly("mxc://s/9", "mxc://s/2").inOrder()
        val sameFile = aProfileMusicTrack(url = "mxc://s/2", title = "Two")
        assertThat(ProfileMusicCodec.withAdded(current, sameFile).map { it.url }).containsExactly("mxc://s/2", "mxc://s/1").inOrder()
    }

    @Test
    fun `the list keeps at most 30 newest tracks`() {
        val current = (1..30).map { aProfileMusicTrack(url = "mxc://s/$it", title = "T$it") }
        val updated = ProfileMusicCodec.withAdded(current, aProfileMusicTrack(url = "mxc://s/new", title = "New"))
        assertThat(updated).hasSize(ProfileMusicCodec.MAX_TRACKS)
        assertThat(updated.first().url).isEqualTo("mxc://s/new")
        assertThat(updated.last().url).isEqualTo("mxc://s/29")
    }

    @Test
    fun `the list is cut to fit the profile size limit`() {
        val longTitle = "x".repeat(4_000)
        val tracks = (1..20).map { aProfileMusicTrack(url = "mxc://s/$it", title = longTitle) }
        val limited = ProfileMusicCodec.limit(tracks)
        assertThat(limited.size).isLessThan(tracks.size)
        assertThat(limited.first().url).isEqualTo("mxc://s/1")
        val size = ProfileMusicCodec.encodeBody(ProfileMusicCodec.encodeValue(limited)).toByteArray().size
        assertThat(size).isAtMost(ProfileMusicCodec.MAX_BYTES)
    }

    @Test
    fun `removing matches the file or the source`() {
        val current = listOf(
            aProfileMusicTrack(url = "mxc://s/1").copy(origin = "o1"),
            aProfileMusicTrack(url = "mxc://s/2").copy(origin = "o2"),
        )
        assertThat(ProfileMusicCodec.withRemoved(current, url = "mxc://s/1", origin = null).map { it.url }).containsExactly("mxc://s/2")
        assertThat(ProfileMusicCodec.withRemoved(current, url = "mxc://other", origin = "o2").map { it.url }).containsExactly("mxc://s/1")
    }

    @Test
    fun `the origin is a stable hash, not the encrypted url`() {
        val track = aMusicTrack(id = "a")
        val origin = ProfileMusicCodec.originOf(track)
        assertThat(origin).hasLength(32)
        assertThat(origin).isEqualTo(ProfileMusicCodec.originOf(track.copy(id = "other")))
        assertThat(origin).doesNotContain("mxc")
    }
}
