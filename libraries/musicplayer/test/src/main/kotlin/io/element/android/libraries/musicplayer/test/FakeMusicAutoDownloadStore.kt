/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.test

import io.element.android.libraries.musicplayer.api.MusicAutoDownload
import io.element.android.libraries.musicplayer.api.MusicAutoDownloadStore
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMusicAutoDownloadStore(
    initial: MusicAutoDownload = MusicAutoDownload.Default,
) : MusicAutoDownloadStore {
    override val settings = MutableStateFlow(initial)

    override fun update(settings: MusicAutoDownload) {
        this.settings.value = settings
    }
}
