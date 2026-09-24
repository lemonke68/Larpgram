/*
 * Правка форка: прошедший звонок как в Telegram. В ЛС — пузырь со стороны звонившего:
 * «Исходящий / Входящий / Отклонённый звонок», под ним стрелка и время, справа трубка —
 * перезвонить (`ChatMessageCell` с `MessageObject.isVoiceCall`). В группе — служебная таблетка
 * по центру, как «Видеочат начат».
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.TimelineRoomInfo
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.bubble.BubbleState
import io.element.android.features.messages.impl.timeline.model.event.RtcNotificationState
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRtcNotificationContent
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.floatingDateBadgeBackground
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
internal fun TgCallNotifyView(
    timelineRoomInfo: TimelineRoomInfo,
    event: TimelineItem.Event,
    content: TimelineItemRtcNotificationContent,
    state: RtcNotificationState.Tombstoned,
    onLongClick: (TimelineItem.Event) -> Unit,
    onCallBackClick: (isAudioCall: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!timelineRoomInfo.isDm) {
        GroupCallServiceMessage(content = content, event = event, modifier = modifier)
        return
    }
    val isAudio = content.callIntent == CallIntent.AUDIO
    val declined = state as? RtcNotificationState.Declined
    val title = when {
        declined != null && event.isMine -> CommonStrings.larpgram_call_cancelled
        declined != null -> CommonStrings.larpgram_call_declined
        event.isMine -> if (isAudio) CommonStrings.larpgram_call_outgoing else CommonStrings.larpgram_call_outgoing_video
        else -> if (isAudio) CommonStrings.larpgram_call_incoming else CommonStrings.larpgram_call_incoming_video
    }
    val isFailed = declined != null
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = if (event.isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        MessageEventBubble(
            state = BubbleState(
                groupPosition = event.groupPosition,
                isMine = event.isMine,
                timelineRoomInfo = timelineRoomInfo,
            ),
            interactionSource = remember { MutableInteractionSource() },
            onClick = {},
            onLongClick = { onLongClick(event) },
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(title),
                        style = ElementTheme.typography.fontBodyLgMedium,
                        color = ElementTheme.colors.textPrimary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Стрелка направления: зелёная — состоялся, красная — отклонён.
                        Icon(
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(if (event.isMine) -45f else 135f),
                            imageVector = CompoundIcons.ArrowRight(),
                            contentDescription = null,
                            tint = if (isFailed) ElementTheme.colors.iconCriticalPrimary else ElementTheme.colors.iconSuccessPrimary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = event.sentTime,
                            style = ElementTheme.typography.fontBodySmRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onCallBackClick(isAudio) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isAudio) CompoundIcons.VoiceCallSolid() else CompoundIcons.VideoCallSolid(),
                        contentDescription = stringResource(CommonStrings.action_call),
                        tint = ElementTheme.colors.iconAccentPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCallServiceMessage(
    content: TimelineItemRtcNotificationContent,
    event: TimelineItem.Event,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ElementTheme.colors.floatingDateBadgeBackground,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.size(16.dp),
                    imageVector = if (content.callIntent == CallIntent.AUDIO) CompoundIcons.VoiceCallSolid() else CompoundIcons.VideoCallSolid(),
                    contentDescription = null,
                    tint = ElementTheme.colors.iconPrimary,
                )
                Text(
                    text = stringResource(CommonStrings.larpgram_call_group_started, event.safeSenderName, event.sentTime),
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = ElementTheme.colors.textPrimary,
                )
            }
        }
    }
}
