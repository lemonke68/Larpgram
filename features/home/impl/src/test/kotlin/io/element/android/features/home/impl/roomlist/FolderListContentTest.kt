/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class FolderListContentTest {
    @Test
    fun `transient empty batch keeps the selected folder rows`() {
        val cached = aRoomsContentState()
        val empty = aRoomsContentState(summaries = persistentListOf())
        assertThat(roomListContentDuringFolderSwitch(empty, cached, true)).isSameInstanceAs(cached)
    }

    @Test
    fun `empty folder after response or deadline never shows stale rooms`() {
        val cached = aRoomsContentState()
        val empty = aRoomsContentState(summaries = persistentListOf())
        assertThat(roomListContentDuringFolderSwitch(empty, cached, false)).isSameInstanceAs(empty)
    }

    @Test
    fun `fresh rows always replace the cache`() {
        val cached = aRoomsContentState()
        val live = aRoomsContentState(showUnreadCount = true)
        assertThat(roomListContentDuringFolderSwitch(live, cached, true)).isSameInstanceAs(live)
    }

    @Test
    fun `uncached and globally empty folders keep their live state`() {
        val empty = aRoomsContentState(summaries = persistentListOf())
        assertThat(roomListContentDuringFolderSwitch(empty, null, true)).isSameInstanceAs(empty)
        val globallyEmpty = anEmptyContentState()
        assertThat(roomListContentDuringFolderSwitch(globallyEmpty, aRoomsContentState(), true)).isSameInstanceAs(globallyEmpty)
    }
}
