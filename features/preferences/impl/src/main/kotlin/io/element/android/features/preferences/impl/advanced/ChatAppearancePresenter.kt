/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.theme.ChatThemeOption
import io.element.android.libraries.designsystem.theme.ChatWallpaperOption
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.preferences.api.store.ChatAppearanceStore
import io.element.android.libraries.preferences.api.store.DEFAULT_BUBBLE_CORNER_RADIUS_DP
import io.element.android.libraries.preferences.api.store.DEFAULT_MESSAGE_TEXT_SIZE_SP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class ChatAppearanceState(
    val messageTextSizeSp: Int,
    val bubbleCornerRadiusDp: Int,
    val chatWallpaperId: String,
    val chatWallpaperCustomColorArgb: Int?,
    val chatBubbleColorArgb: Int?,
    val chatAccentColorArgb: Int?,
    val chatWallpaperImageUri: String?,
    val chatWallpaperGradientSpec: String?,
    val chatListThreeLine: Boolean,
    val eventSink: (ChatAppearanceEvent) -> Unit,
)

sealed interface ChatAppearanceEvent {
    data class SetMessageTextSize(val sizeSp: Int) : ChatAppearanceEvent
    data class SetBubbleCornerRadius(val radiusDp: Int) : ChatAppearanceEvent
    data class SetChatWallpaper(val id: String) : ChatAppearanceEvent
    data class SetChatWallpaperCustomColor(val argb: Int) : ChatAppearanceEvent
    data class SetChatBubbleColor(val argb: Int?) : ChatAppearanceEvent
    data class SetChatAccentColor(val argb: Int?) : ChatAppearanceEvent
    data class SetChatWallpaperImage(val uri: String?) : ChatAppearanceEvent
    data class SetChatWallpaperGradient(val spec: String?) : ChatAppearanceEvent
    data class SetChatListThreeLine(val enabled: Boolean) : ChatAppearanceEvent
    data class ApplyChatTheme(val themeId: String) : ChatAppearanceEvent
}

fun aChatAppearanceState(
    messageTextSizeSp: Int = DEFAULT_MESSAGE_TEXT_SIZE_SP,
    bubbleCornerRadiusDp: Int = DEFAULT_BUBBLE_CORNER_RADIUS_DP,
    chatWallpaperId: String = ChatWallpaperOption.DEFAULT.id,
    chatWallpaperCustomColorArgb: Int? = null,
    chatBubbleColorArgb: Int? = null,
    chatAccentColorArgb: Int? = null,
    chatWallpaperImageUri: String? = null,
    chatWallpaperGradientSpec: String? = null,
    chatListThreeLine: Boolean = false,
    eventSink: (ChatAppearanceEvent) -> Unit = {},
) = ChatAppearanceState(
    messageTextSizeSp = messageTextSizeSp,
    bubbleCornerRadiusDp = bubbleCornerRadiusDp,
    chatWallpaperId = chatWallpaperId,
    chatWallpaperCustomColorArgb = chatWallpaperCustomColorArgb,
    chatBubbleColorArgb = chatBubbleColorArgb,
    chatAccentColorArgb = chatAccentColorArgb,
    chatWallpaperImageUri = chatWallpaperImageUri,
    chatWallpaperGradientSpec = chatWallpaperGradientSpec,
    chatListThreeLine = chatListThreeLine,
    eventSink = eventSink,
)

/** The chat appearance settings (text size, bubbles, wallpaper, colours, chat list density). */
@Inject
class ChatAppearancePresenter(
    private val chatAppearanceStore: ChatAppearanceStore,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
) : Presenter<ChatAppearanceState> {
    @Composable
    override fun present(): ChatAppearanceState {
        val messageTextSizeSp by remember {
            chatAppearanceStore.getMessageTextSizeSpFlow()
        }.collectAsState(initial = DEFAULT_MESSAGE_TEXT_SIZE_SP)
        val bubbleCornerRadiusDp by remember {
            chatAppearanceStore.getBubbleCornerRadiusDpFlow()
        }.collectAsState(initial = DEFAULT_BUBBLE_CORNER_RADIUS_DP)
        val chatWallpaperId by remember {
            chatAppearanceStore.getChatWallpaperIdFlow()
        }.collectAsState(initial = null)
        val chatWallpaperCustomColorArgb by remember {
            chatAppearanceStore.getChatWallpaperCustomColorArgbFlow()
        }.collectAsState(initial = null)
        val chatBubbleColorArgb by remember {
            chatAppearanceStore.getChatBubbleColorArgbFlow()
        }.collectAsState(initial = null)
        val chatAccentColorArgb by remember {
            chatAppearanceStore.getChatAccentColorArgbFlow()
        }.collectAsState(initial = null)
        val chatWallpaperImageUri by remember {
            chatAppearanceStore.getChatWallpaperImageUriFlow()
        }.collectAsState(initial = null)
        val chatListThreeLine by remember {
            chatAppearanceStore.getChatListThreeLineFlow()
        }.collectAsState(initial = false)
        val chatWallpaperGradientSpec by remember {
            chatAppearanceStore.getChatWallpaperGradientFlow()
        }.collectAsState(initial = null)

        fun handleEvent(event: ChatAppearanceEvent) {
            sessionCoroutineScope.launch {
                when (event) {
                    is ChatAppearanceEvent.SetMessageTextSize -> chatAppearanceStore.setMessageTextSizeSp(event.sizeSp)
                    is ChatAppearanceEvent.SetBubbleCornerRadius -> chatAppearanceStore.setBubbleCornerRadiusDp(event.radiusDp)
                    is ChatAppearanceEvent.SetChatWallpaper -> chatAppearanceStore.setChatWallpaperId(event.id)
                    is ChatAppearanceEvent.SetChatWallpaperCustomColor -> {
                        // Сохраняем цвет и переключаем выбор на кастомный маркер-id.
                        chatAppearanceStore.setChatWallpaperCustomColorArgb(event.argb)
                        chatAppearanceStore.setChatWallpaperId(ChatWallpaperOption.CUSTOM_ID)
                    }
                    is ChatAppearanceEvent.SetChatBubbleColor -> chatAppearanceStore.setChatBubbleColorArgb(event.argb)
                    is ChatAppearanceEvent.SetChatAccentColor -> chatAppearanceStore.setChatAccentColorArgb(event.argb)
                    is ChatAppearanceEvent.SetChatListThreeLine -> chatAppearanceStore.setChatListThreeLine(event.enabled)
                    is ChatAppearanceEvent.SetChatWallpaperGradient -> {
                        // Градиент задан: сохраняем спеку и переводим id обоев на «градиент». Сброс (null)
                        // — очищаем и возвращаем паттерн.
                        chatAppearanceStore.setChatWallpaperGradient(event.spec)
                        chatAppearanceStore.setChatWallpaperId(
                            if (event.spec != null) ChatWallpaperOption.CUSTOM_GRADIENT_ID else ChatWallpaperOption.DEFAULT.id
                        )
                    }
                    is ChatAppearanceEvent.SetChatWallpaperImage -> {
                        // Фото выбрано: сохраняем URI и переводим маркер обоев на «фото». Сброс (null) —
                        // очищаем URI и возвращаем обои к дефолтному паттерну.
                        chatAppearanceStore.setChatWallpaperImageUri(event.uri)
                        chatAppearanceStore.setChatWallpaperId(
                            if (event.uri != null) ChatWallpaperOption.CUSTOM_IMAGE_ID else ChatWallpaperOption.DEFAULT.id
                        )
                    }
                    is ChatAppearanceEvent.ApplyChatTheme -> {
                        // Пресет = связка: ставим обои, цвет пузыря и акцент разом, палитра согласована.
                        val theme = ChatThemeOption.entries.first { it.id == event.themeId }
                        chatAppearanceStore.setChatWallpaperId(theme.wallpaper.id)
                        chatAppearanceStore.setChatBubbleColorArgb(theme.bubbleColor?.toArgb())
                        chatAppearanceStore.setChatAccentColorArgb(theme.accentColor?.toArgb())
                    }
                }
            }
        }

        return ChatAppearanceState(
            messageTextSizeSp = messageTextSizeSp,
            bubbleCornerRadiusDp = bubbleCornerRadiusDp,
            chatWallpaperId = chatWallpaperId ?: ChatWallpaperOption.DEFAULT.id,
            chatWallpaperCustomColorArgb = chatWallpaperCustomColorArgb,
            chatBubbleColorArgb = chatBubbleColorArgb,
            chatAccentColorArgb = chatAccentColorArgb,
            chatWallpaperImageUri = chatWallpaperImageUri,
            chatWallpaperGradientSpec = chatWallpaperGradientSpec,
            chatListThreeLine = chatListThreeLine,
            eventSink = ::handleEvent,
        )
    }
}
