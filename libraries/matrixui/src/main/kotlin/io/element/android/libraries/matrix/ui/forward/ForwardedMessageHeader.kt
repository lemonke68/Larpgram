/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.forward

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.forward.ForwardedMessage
import io.element.android.libraries.matrix.ui.R
import kotlinx.coroutines.flow.flowOf

@Composable
fun ForwardedMessageHeader(
    source: ForwardedMessage,
    onAuthorClick: (ForwardedMessage) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = ElementTheme.colors.textActionAccent,
) {
    val privacy = LocalForwardingPrivacy.current
    val revision by (privacy?.revision ?: flowOf(0)).collectAsState(initial = 0)
    val canLink by produceState(source.isChannel && source.roomId != null, source, privacy, revision) {
        value = when {
            source.isChannel -> source.roomId != null
            else -> source.userId?.let { privacy?.allowed(UserId(it)) } == true
        }
    }
    val name = source.name?.takeIf(String::isNotBlank) ?: stringResource(
        if (source.isChannel) R.string.larpgram_forwarded_channel_unknown else R.string.larpgram_forwarded_author_unknown
    )
    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.larpgram_forwarded_from),
            style = ElementTheme.typography.fontBodyXsRegular,
            color = color.copy(alpha = 0.8f),
        )
        Row(
            modifier = if (canLink) Modifier.clickable { onAuthorClick(source) } else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (canLink) {
                Avatar(
                    avatarData = AvatarData(
                        id = source.userId ?: source.roomId.orEmpty(),
                        name = name,
                        url = source.avatarUrl,
                        size = AvatarSize.InviteSender,
                    ),
                    avatarType = if (source.isChannel) AvatarType.Room() else AvatarType.User,
                )
            }
            Text(
                text = name,
                style = ElementTheme.typography.fontBodySmMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
