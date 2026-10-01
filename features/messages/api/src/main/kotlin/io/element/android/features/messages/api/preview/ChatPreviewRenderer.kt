/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.api.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.element.android.libraries.matrix.api.core.RoomId

/**
 * Лента чата «только посмотреть» для превью по долгому нажатию в списке чатов, как в Telegram
 * (`DialogsActivity.showChatPreview`): листается, но ничего не отмечает прочитанным, не гасит
 * уведомления и не даёт действий с сообщениями.
 */
interface ChatPreviewRenderer {
    @Composable
    fun Preview(
        roomId: RoomId,
        onClick: () -> Unit,
        modifier: Modifier,
    )
}
