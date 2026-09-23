/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.avatar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AvatarDataInitialsTest {
    private fun initials(name: String?, id: String = "@user:server") = AvatarData(id, name, null, AvatarSize.RoomListItem).initials

    @Test
    fun `single word gives one letter`() {
        assertThat(initials("lin")).isEqualTo("L")
    }

    @Test
    fun `two words give first letters of both`() {
        assertThat(initials("Иван Петров")).isEqualTo("ИП")
    }

    @Test
    fun `more words take the last word`() {
        assertThat(initials("Анна Мария Смит")).isEqualTo("АС")
    }

    @Test
    fun `last word starting with a dash is ignored`() {
        assertThat(initials("test-channel —")).isEqualTo("T")
        assertThat(initials("test-channel — comments")).isEqualTo("TC")
    }

    @Test
    fun `extra whitespace is ignored`() {
        assertThat(initials("  pavel   durov  ")).isEqualTo("PD")
    }

    @Test
    fun `no name falls back to the id letter`() {
        assertThat(initials(null, id = "@alice:server")).isEqualTo("A")
    }

    @Test
    fun `emoji first word is kept`() {
        assertThat(initials("🎮 Gamers")).isEqualTo("🎮G")
    }
}
