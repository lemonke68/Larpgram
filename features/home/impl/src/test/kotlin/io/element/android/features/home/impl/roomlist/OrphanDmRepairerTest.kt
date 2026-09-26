/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomAlias
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.aRoomMember
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.json.JSONObject
import org.junit.Test

class OrphanDmRepairerTest : RobolectricTest() {
    private fun aTwoPersonRoom(
        id: RoomId = A_ROOM_ID,
        rawName: String? = null,
        isDirect: Boolean = false,
        activeMembersCount: Long = 2,
        canonicalAlias: RoomAlias? = null,
        isPublic: Boolean = false,
    ) = aRoomInfo(
        id = id,
        rawName = rawName,
        isDirect = isDirect,
        isPublic = isPublic,
        activeMembersCount = activeMembersCount,
        canonicalAlias = canonicalAlias,
        // Героев у «сироты» SDK не отдаёт (только у ЛС), поэтому их нет и в фикстуре.
        heroes = emptyList(),
    )

    @Test
    fun `an unnamed two-person room missing from m direct is an orphan DM`() {
        val result = findOrphanDmCandidates(listOf(aTwoPersonRoom()))
        assertThat(result).containsExactly(A_ROOM_ID)
    }

    @Test
    fun `the other member is the single active member besides us`() {
        val members = listOf(
            aRoomMember(userId = A_USER_ID),
            aRoomMember(userId = A_USER_ID_2),
            aRoomMember(userId = UserId("@gone:server.org"), membership = RoomMembershipState.LEAVE),
        )
        assertThat(otherActiveMember(members, me = A_USER_ID)).isEqualTo(A_USER_ID_2)
        assertThat(otherActiveMember(listOf(aRoomMember(userId = A_USER_ID)), me = A_USER_ID)).isNull()
    }

    @Test
    fun `named, public, aliased, bigger or already direct rooms are left alone`() {
        val rooms = listOf(
            aTwoPersonRoom(rawName = "Новости"),
            aTwoPersonRoom(isPublic = true),
            aTwoPersonRoom(canonicalAlias = RoomAlias("#room:server.org")),
            aTwoPersonRoom(activeMembersCount = 3),
            aTwoPersonRoom(isDirect = true),
        )
        assertThat(findOrphanDmCandidates(rooms)).isEmpty()
    }

    @Test
    fun `merge keeps existing DMs and adds the orphan`() {
        val current = """{"@carol:server.org":["!other:server.org"]}"""

        val merged = mergeIntoDirect(current, mapOf(A_ROOM_ID to A_USER_ID_2))

        val json = JSONObject(merged!!)
        assertThat(json.getJSONArray("@carol:server.org").getString(0)).isEqualTo("!other:server.org")
        assertThat(json.getJSONArray(A_USER_ID_2.value).getString(0)).isEqualTo(A_ROOM_ID.value)
    }

    @Test
    fun `merge appends to the same user's list and skips rooms already there`() {
        val current = """{"${A_USER_ID_2.value}":["${A_ROOM_ID.value}"]}"""

        assertThat(mergeIntoDirect(current, mapOf(A_ROOM_ID to A_USER_ID_2))).isNull()
        val merged = JSONObject(mergeIntoDirect(current, mapOf(A_ROOM_ID_2 to A_USER_ID_2))!!)
        assertThat(merged.getJSONArray(A_USER_ID_2.value).length()).isEqualTo(2)
    }

    @Test
    fun `merge refuses to overwrite unparsable content`() {
        assertThat(mergeIntoDirect("not json", mapOf(A_ROOM_ID to A_USER_ID_2))).isNull()
    }
}
