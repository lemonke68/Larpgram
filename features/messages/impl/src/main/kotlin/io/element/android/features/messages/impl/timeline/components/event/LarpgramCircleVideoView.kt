/*
 * Правка форка: кружочки, круглые видеосообщения как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.protection.ProtectedView
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Кружочек в таймлайне: квадратное видео, обрезанное в круг.
 *
 * Как отличаем кружочек от обычного видео: по имени файла. Штатная отправка
 * (`Timeline.sendVideo`) не даёт добавить своё поле в content, а отказаться от неё нельзя,
 * иначе в шифрованных комнатах медиа поедет незашифрованным. Имя файла до клиента
 * доезжает, поэтому маркер живёт там.
 */
@Composable
fun LarpgramCircleVideoView(
    content: TimelineItemVideoContent,
    hideMediaContent: Boolean,
    onContentClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onShowContentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = content.caption ?: stringResource(CommonStrings.common_video)

    Box(
        modifier = modifier
            .size(CIRCLE_SIZE)
            .clip(CircleShape)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        ProtectedView(
            hideContent = hideMediaContent,
            onShowClick = onShowContentClick,
        ) {
            AsyncImage(
                modifier = Modifier
                    .size(CIRCLE_SIZE)
                    .clip(CircleShape)
                    .then(
                        if (onContentClick != null) {
                            Modifier.combinedClickable(
                                onClick = onContentClick,
                                onLongClick = onLongClick,
                                onLongClickLabel = stringResource(CommonStrings.action_open_context_menu),
                            )
                        } else {
                            Modifier
                        }
                    ),
                model = MediaRequestData(
                    source = content.thumbnailSource ?: content.mediaSource,
                    kind = MediaRequestData.Kind.Thumbnail(
                        width = CIRCLE_THUMBNAIL_PX,
                        height = CIRCLE_THUMBNAIL_PX,
                    ),
                ),
                // Кадр квадратный, но обрезаем на всякий случай: чужой клиент мог прислать не квадрат.
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                contentDescription = description,
            )
            Icon(
                modifier = Modifier.size(40.dp),
                imageVector = CompoundIcons.PlaySolid(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

/** Как в Telegram: кружок занимает заметную, но не всю ширину. */
private val CIRCLE_SIZE = 200.dp
private const val CIRCLE_THUMBNAIL_PX = 400L
