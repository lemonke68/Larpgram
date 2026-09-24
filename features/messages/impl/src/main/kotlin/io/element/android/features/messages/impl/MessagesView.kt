/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import dev.chrisbanes.haze.rememberHazeState
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.circles.impl.CircleRecorderEvents
import io.element.android.features.circles.impl.CircleRecorderView
import io.element.android.features.gifs.impl.GifPickerEvents
import io.element.android.features.gifs.impl.GifPickerView
import io.element.android.features.location.api.LiveLocationSharingBanner
import io.element.android.features.messages.api.timeline.voicemessages.composer.VoiceMessageComposerEvent
import io.element.android.features.messages.impl.actionlist.ActionListEvent
import io.element.android.features.messages.impl.actionlist.ActionListState
import io.element.android.features.messages.impl.actionlist.ActionListView
import io.element.android.features.messages.impl.actionlist.LocalMessageActionsAnchor
import io.element.android.features.messages.impl.actionlist.MessageActionsAnchor
import io.element.android.features.messages.impl.actionlist.MessageActionsOverlay
import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import io.element.android.features.messages.impl.attachments.tgattach.TgAttachAction
import io.element.android.features.messages.impl.attachments.tgattach.TgAttachSheet
import io.element.android.features.messages.impl.crypto.identity.IdentityChangeStateView
import io.element.android.features.messages.impl.link.LinkEvent
import io.element.android.features.messages.impl.link.LinkView
import io.element.android.features.messages.impl.messagecomposer.DisabledComposerView
import io.element.android.features.messages.impl.messagecomposer.MessageComposerEvent
import io.element.android.features.messages.impl.messagecomposer.MessageComposerView
import io.element.android.features.messages.impl.messagecomposer.TgMediaPanel
import io.element.android.features.messages.impl.messagecomposer.TgMediaPanelController
import io.element.android.features.messages.impl.messagecomposer.TgMediaPanelTab
import io.element.android.features.messages.impl.messagecomposer.rememberTgMediaPanelController
import io.element.android.features.messages.impl.messagecomposer.suggestions.SuggestionsPickerView
import io.element.android.features.messages.impl.pinned.banner.PinnedMessagesBannerState
import io.element.android.features.messages.impl.pinned.banner.PinnedMessagesBannerView
import io.element.android.features.messages.impl.pinned.banner.PinnedMessagesBannerViewDefaults
import io.element.android.features.messages.impl.timeline.FOCUS_ON_PINNED_EVENT_DEBOUNCE_DURATION_IN_MILLIS
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.TimelineView
import io.element.android.features.messages.impl.timeline.aGroupedEvents
import io.element.android.features.messages.impl.timeline.aTimelineItemDaySeparator
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineState
import io.element.android.features.messages.impl.timeline.components.CallMenuItem
import io.element.android.features.messages.impl.timeline.components.customreaction.CustomReactionEvent
import io.element.android.features.messages.impl.timeline.components.event.LocalCircleMediaLoader
import io.element.android.features.messages.impl.timeline.components.event.LocalOpenStickerPack
import io.element.android.features.messages.impl.timeline.components.event.StickerPackSheet
import io.element.android.features.messages.impl.timeline.components.reactionsummary.ReactionSummaryEvent
import io.element.android.features.messages.impl.timeline.components.reactionsummary.ReactionSummaryView
import io.element.android.features.messages.impl.timeline.components.receipt.bottomsheet.ReadReceiptBottomSheet
import io.element.android.features.messages.impl.timeline.components.receipt.bottomsheet.ReadReceiptBottomSheetEvent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemStateEventContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.sendfailure.SendFailureDialogView
import io.element.android.features.messages.impl.topbars.DmPresence
import io.element.android.features.messages.impl.topbars.TgChatHeader
import io.element.android.features.messages.impl.topbars.ThreadTopBar
import io.element.android.features.messages.impl.voicemessages.composer.VoiceMessagePermissionRationaleDialog
import io.element.android.features.messages.impl.voicemessages.composer.VoiceMessageSendingFailedDialog
import io.element.android.features.roomcall.api.RoomCallState
import io.element.android.features.stickers.impl.StickerPickerEvents
import io.element.android.features.stickers.impl.StickerSendErrorDialog
import io.element.android.features.stickers.impl.TgStickerPanel
import io.element.android.libraries.androidutils.ui.hideKeyboard
import io.element.android.libraries.androidutils.ui.showKeyboard
import io.element.android.libraries.designsystem.atomic.molecules.ComposerAlertMolecule
import io.element.android.libraries.designsystem.components.ExpandableBottomSheetLayout
import io.element.android.libraries.designsystem.components.ExpandableBottomSheetLayoutState
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.glass.LocalChatBottomOverlayHeight
import io.element.android.libraries.designsystem.components.glass.LocalChatGlassState
import io.element.android.libraries.designsystem.components.glass.LocalChatTopOverlayHeight
import io.element.android.libraries.designsystem.components.rememberExpandableBottomSheetLayoutState
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.text.toAnnotatedString
import io.element.android.libraries.designsystem.text.toDp
import io.element.android.libraries.designsystem.theme.components.BottomSheetDragHandle
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.HideKeyboardWhenDisposed
import io.element.android.libraries.designsystem.utils.KeepScreenOn
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent
import io.element.android.libraries.designsystem.utils.rememberBlurredBackdrop
import io.element.android.libraries.designsystem.utils.scaffoldScrollableContentInsets
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.emoji.api.picker.EmojiPickerRenderer
import io.element.android.libraries.emoji.api.picker.NoOpEmojiPickerRenderer
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.identity.IdentityState
import io.element.android.libraries.matrix.api.room.tombstone.SuccessorRoom
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.media.contentvalidation.ContentValidationValue
import io.element.android.libraries.matrix.ui.media.contentvalidation.LocalEventContentValidationState
import io.element.android.libraries.textcomposer.CircleRecordGestures
import io.element.android.libraries.textcomposer.model.TextEditorState
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.wysiwyg.link.Link
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MessagesView(
    state: MessagesState,
    onBackClick: () -> Unit,
    onRoomDetailsClick: () -> Unit,
    onEventContentClick: (isLive: Boolean, event: TimelineItem.Event) -> Boolean,
    onGalleryEventItemClick: (isLive: Boolean, event: TimelineItem.Event, index: Int) -> Boolean,
    onUserDataClick: (UserId) -> Unit,
    onLinkClick: (String, Boolean) -> Unit,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    onViewAllPinnedMessagesClick: () -> Unit,
    onThreadsListClick: () -> Unit,
    knockRequestsBannerView: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    forceJumpToBottomVisibility: Boolean = false,
    customReactionBottomSheet: @Composable () -> Unit,
    // Правка форка (фаза 3): рендерер пикера эмодзи для инлайн-разворота по «+» в оверлее.
    emojiPickerRenderer: EmojiPickerRenderer,
    // Правка форка: клавиатура эмодзи для панели Telegram под полем ввода (модификатор, вставка).
    emojiKeyboard: (@Composable (Modifier, (String) -> Unit) -> Unit)? = null,
    // Правка форка: «в сети / был(а)» собеседника ЛС для шапки Telegram.
    dmPresence: DmPresence? = null,
) {
    val eventContentValidationState = LocalEventContentValidationState.current

    OnLifecycleEvent { _, event ->
        state.voiceMessageComposerState.eventSink(VoiceMessageComposerEvent.LifecycleEvent(event))
    }

    KeepScreenOn(state.voiceMessageComposerState.keepScreenOn)

    HideKeyboardWhenDisposed()

    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)

    var maxComposerHeightPx by remember { mutableIntStateOf(120) }

    // Правка форка: плавающее поле ввода Telegram 12. Лента уходит под него и размывается под
    // стеклом (haze), поэтому знает высоту панели снизу. В режиме форматирования и подсказок
    // упоминаний шторка апстримовская — непрозрачная, лента над ней.
    val chatGlassState = rememberHazeState()
    val mediaPanel = rememberTgMediaPanelController()
    var composerOverlayHeight by remember { mutableStateOf(0.dp) }
    // Правка форка: шапка Telegram плавает поверх ленты (обои под статус-баром); в треде — апстримовская.
    val isThreadTimeline = state.timelineState.timelineMode is Timeline.Mode.Thread
    var headerOverlayHeight by remember { mutableStateOf(0.dp) }
    val floatingComposer = !state.composerState.showTextFormatting && state.composerState.suggestions.isEmpty()

    // This is needed because the composer is inside an AndroidView that can't be affected by the FocusManager in Compose
    val localView = LocalView.current

    // Правка форка: якорь меню долгого нажатия. Пузыри регистрируют сюда координаты по id,
    // onMessageLongClick читает их. Объявлен до обработчика, чтобы тот его видел.
    val messageActionsAnchor = remember { MessageActionsAnchor() }

    fun hidingKeyboard(block: () -> Unit) {
        localView.hideKeyboard()
        block()
    }

    fun onContentClick(event: TimelineItem.Event) {
        Timber.v("onMessageClick= ${event.id}")
        val eventId = event.eventId
        if (eventId != null && eventContentValidationState[eventId].getCurrentOverallState() != ContentValidationValue.Valid) return

        val hideKeyboard = onEventContentClick(state.timelineState.isLive, event)
        if (hideKeyboard) {
            localView.hideKeyboard()
        }
    }

    fun onMessageLongClick(event: TimelineItem.Event) {
        Timber.v("OnMessageLongClicked= ${event.id}")
        // Правка форка: координаты пузыря для привязанного меню. Один общий обработчик на все
        // пути открытия, поэтому спрашиваем по id, а не ловим в колбэке конкретного нажатия.
        messageActionsAnchor.bubbleBounds = messageActionsAnchor.boundsFor(event.id.value)
        hidingKeyboard {
            state.actionListState.eventSink(
                ActionListEvent.ComputeForMessage(
                    event = event,
                    userEventPermissions = state.userEventPermissions,
                )
            )
        }
    }

    fun onActionSelected(action: TimelineItemAction, event: TimelineItem.Event) {
        state.eventSink(MessagesEvent.HandleAction(action, event))
    }

    fun onEmojiReactionClick(emoji: String, event: TimelineItem.Event) {
        state.eventSink(MessagesEvent.ToggleReaction(emoji, event.eventOrTransactionId))
    }

    fun onEmojiReactionLongClick(emoji: String, event: TimelineItem.Event) {
        if (event.eventId == null) return
        state.reactionSummaryState.eventSink(ReactionSummaryEvent.ShowReactionSummary(event.eventId, event.reactionsState.reactions, emoji))
    }

    fun onMoreReactionsClick(event: TimelineItem.Event) {
        state.customReactionState.eventSink(CustomReactionEvent.ShowCustomReactionSheet(event))
    }

    CompositionLocalProvider(
        LocalMessageActionsAnchor provides messageActionsAnchor,
        LocalChatGlassState provides chatGlassState,
        LocalChatBottomOverlayHeight provides if (floatingComposer) composerOverlayHeight else 0.dp,
        LocalChatTopOverlayHeight provides if (isThreadTimeline) 0.dp else headerOverlayHeight,
    ) {
    val expandableState = rememberExpandableBottomSheetLayoutState()
    val density = LocalDensity.current
    // Правка форка: меню вложений Telegram рисуется поверх всего экрана чата, поэтому корень — Box.
    Box(modifier = modifier.fillMaxSize()) {
    ExpandableBottomSheetLayout(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            // Правка форка: низ не отступаем — обои и лента идут под панель навигации, отступ
            // от неё берёт поле ввода (navigationBarsPadding в шторке).
            .windowInsetsPadding(
                WindowInsets.systemBars.only(
                    if (isThreadTimeline) WindowInsetsSides.Top + WindowInsetsSides.Horizontal else WindowInsetsSides.Horizontal
                )
            )
            .onSizeChanged { size ->
                // Let the composer takes at max half of the available height.
                // The value will be different if the soft keyboard is displayed
                // or not.
                maxComposerHeightPx = (size.height * 0.5f).toInt()
            },
        content = {
            Scaffold(
                contentWindowInsets = if (isThreadTimeline) scaffoldScrollableContentInsets else WindowInsets(0),
                topBar = {
                    if (state.timelineState.timelineMode is Timeline.Mode.Thread) {
                        ThreadTopBar(
                            roomName = state.roomName,
                            roomAvatarData = state.roomAvatar,
                            heroes = state.heroes,
                            isTombstoned = state.isTombstoned,
                            onBackClick = onBackClick,
                        )
                    }
                },
                content = { padding ->
                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .consumeWindowInsets(padding)
                    ) {
                        MessagesViewContent(
                            state = state,
                            onContentClick = ::onContentClick,
                            onGalleryItemClick = { event, index ->
                                val hideKeyboard = onGalleryEventItemClick(
                                    state.timelineState.isLive,
                                    event,
                                    index,
                                )
                                if (hideKeyboard) {
                                    localView.hideKeyboard()
                                }
                            },
                            onMessageLongClick = ::onMessageLongClick,
                            onUserDataClick = {
                                hidingKeyboard {
                                    state.eventSink(MessagesEvent.OnUserClicked(it))
                                }
                            },
                            onLinkClick = { link, customTab ->
                                if (customTab) {
                                    onLinkClick(link.url, true)
                                    // Do not check those links, they are internal link only
                                } else {
                                    state.linkState.eventSink(LinkEvent.OnLinkClick(link))
                                }
                            },
                            onReactionClick = ::onEmojiReactionClick,
                            onReactionLongClick = ::onEmojiReactionLongClick,
                            onMoreReactionsClick = ::onMoreReactionsClick,
                            onReadReceiptClick = { event ->
                                state.readReceiptBottomSheetState.eventSink(ReadReceiptBottomSheetEvent.EventSelected(event))
                            },
                            onSwipeToReply = { targetEvent ->
                                state.eventSink(MessagesEvent.HandleAction(TimelineItemAction.Reply, targetEvent))
                            },
                            onJoinCallClick = onJoinCallClick,
                            forceJumpToBottomVisibility = forceJumpToBottomVisibility,
                            onViewAllPinnedMessagesClick = onViewAllPinnedMessagesClick,
                            knockRequestsBannerView = knockRequestsBannerView,
                        )

                        if (!isThreadTimeline) {
                            TgChatHeader(
                                state = state,
                                dmPresence = dmPresence,
                                onBackClick = { hidingKeyboard { onBackClick() } },
                                onRoomDetailsClick = { hidingKeyboard { onRoomDetailsClick() } },
                                onJoinCallClick = onJoinCallClick,
                                onThreadsListClick = onThreadsListClick,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .onSizeChanged { headerOverlayHeight = with(density) { it.height.toDp() } },
                            )
                        }

                        SuggestionsPickerView(
                            modifier = Modifier
                                .shadow(10.dp)
                                .background(ElementTheme.colors.bgCanvasDefault)
                                .align(Alignment.BottomStart)
                                .heightIn(max = 230.dp),
                            roomId = state.roomId,
                            roomName = state.roomName,
                            roomAvatarData = state.roomAvatar,
                            suggestions = state.composerState.suggestions,
                            onSelectSuggestion = {
                                state.composerState.eventSink(MessageComposerEvent.InsertSuggestion(it))
                            }
                        )
                    }
                },
                snackbarHost = {
                    SnackbarHost(
                        snackbarHostState,
                        modifier = Modifier.padding(bottom = LocalChatBottomOverlayHeight.current),
                    )
                },
            )
        },
        bottomSheetContent = {
            Column(
                modifier = Modifier
                    .onSizeChanged { composerOverlayHeight = with(density) { it.height.toDp() } }
                    // Открытая панель эмодзи сама доходит до низа экрана, полосу навигации учитывает она.
                    .then(if (mediaPanel.isVisible) Modifier else Modifier.navigationBarsPadding())
            ) {
                MessagesViewComposerBottomSheetContents(
                    state = state,
                    mediaPanel = mediaPanel,
                    emojiKeyboard = emojiKeyboard,
                    onLinkClick = { url, customTab -> onLinkClick(url, customTab) },
                    onRoomSuccessorClick = { roomId ->
                        state.timelineState.eventSink(TimelineEvent.NavigateToPredecessorOrSuccessorRoom(roomId = roomId))
                    },
                )
            }
        },
        sheetDragHandle = @Composable { toggleAction ->
            if (state.composerState.showTextFormatting) {
                val expandA11yLabel = stringResource(CommonStrings.a11y_expand_message_text_field)
                val collapseA11yLabel = stringResource(CommonStrings.a11y_collapse_message_text_field)
                BottomSheetDragHandle(
                    modifier = Modifier.semantics {
                        role = Role.Button
                        // Accessibility action to toggle the bottom sheet state
                        val label = when (expandableState.position) {
                            ExpandableBottomSheetLayoutState.Position.COLLAPSED, ExpandableBottomSheetLayoutState.Position.DRAGGING -> expandA11yLabel
                            ExpandableBottomSheetLayoutState.Position.EXPANDED -> collapseA11yLabel
                        }
                        onClick(label) {
                            toggleAction()
                            true
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    // Ensure that the bottom sheet is collapsed
                    if (expandableState.position == ExpandableBottomSheetLayoutState.Position.EXPANDED) {
                        toggleAction()
                    }
                }
            }
        },
        isSwipeGestureEnabled = state.composerState.showTextFormatting,
        state = expandableState,
        sheetShape = if (state.composerState.showTextFormatting || state.composerState.suggestions.isNotEmpty()) {
            MaterialTheme.shapes.large
        } else {
            RectangleShape
        },
        maxBottomSheetContentHeight = maxComposerHeightPx.toDp(),
        contentUnderSheet = floatingComposer,
    )

    val showAttachSheet = state.composerState.showAttachmentSourcePicker
    LaunchedEffect(showAttachSheet) {
        if (showAttachSheet) {
            // Клавиатура — Android View, FocusManager Compose её не прячет.
            localView.hideKeyboard()
            mediaPanel.close()
        }
    }
    TgAttachSheet(
        isVisible = showAttachSheet,
        canShareLocation = state.composerState.canShareLocation,
        enableTextFormatting = state.enableTextFormatting,
        onAction = { action ->
            val composerSink = state.composerState.eventSink
            when (action) {
                TgAttachAction.Dismiss -> composerSink(MessageComposerEvent.DismissAttachmentMenu)
                is TgAttachAction.Send -> composerSink(MessageComposerEvent.SendGalleryMedia(action.media, action.caption, action.compress))
                is TgAttachAction.Preview -> composerSink(MessageComposerEvent.PreviewGalleryMedia(action.media))
                TgAttachAction.CameraPhoto -> composerSink(MessageComposerEvent.PickAttachmentSource.PhotoFromCamera)
                TgAttachAction.CameraVideo -> composerSink(MessageComposerEvent.PickAttachmentSource.VideoFromCamera)
                TgAttachAction.SystemGallery -> composerSink(MessageComposerEvent.PickAttachmentSource.FromGallery)
                TgAttachAction.Files -> composerSink(MessageComposerEvent.PickAttachmentSource.FromFiles)
                TgAttachAction.Location -> {
                    composerSink(MessageComposerEvent.PickAttachmentSource.Location)
                    onSendLocationClick()
                }
                TgAttachAction.Poll -> {
                    composerSink(MessageComposerEvent.PickAttachmentSource.Poll)
                    onCreatePollClick()
                }
                TgAttachAction.TextFormatting -> composerSink(MessageComposerEvent.ToggleTextFormatting(enabled = true))
            }
        },
    )
    } // конец Box меню вложений
    } // конец CompositionLocalProvider(LocalMessageActionsAnchor)

    var endPollConfirmingEvent: TimelineItem.Event? by remember { mutableStateOf(null) }

    if (endPollConfirmingEvent != null) {
        ConfirmationDialog(
            content = stringResource(id = CommonStrings.common_poll_end_confirmation),
            onSubmitClick = {
                endPollConfirmingEvent?.let { event ->
                    onActionSelected(TimelineItemAction.EndPoll, event)
                }
                endPollConfirmingEvent = null
            },
            onDismiss = { endPollConfirmingEvent = null },
        )
    }

    // Правка форка: размытый фон под меню долгого нажатия, как в Telegram — попап отделяется
    // от чата и не сливается с ним. Штатного блюра на Android 10 нет, поэтому снимок окна через
    // PixelCopy (rememberBlurredBackdrop). Рисуется отдельным окном (Popup) под шторкой: сама
    // шторка живёт в своём окне выше, скрим у неё лёгкий, поэтому блюр просвечивает.
    val actionTarget = state.actionListState.target as? ActionListState.Target.Success
    val bubbleBounds = messageActionsAnchor.bubbleBounds

    val onSelectActionCommon: (TimelineItemAction, TimelineItem.Event) -> Unit = { action, event ->
        if (action == TimelineItemAction.EndPoll) {
            endPollConfirmingEvent = event
        } else {
            onActionSelected(action, event)
        }
    }
    if (actionTarget != null && bubbleBounds != null) {
        // Правка форка (фаза 2): координаты пузыря есть — привязанное меню. Блюр рисуется
        // внутри оверлея нижним слоем: отдельным окном он приезжал асинхронно и ложился
        // поверх меню.
        MessageActionsOverlay(
            target = actionTarget,
            bubbleLeft = bubbleBounds.left.toInt(),
            bubbleTop = bubbleBounds.top.toInt(),
            bubbleRight = bubbleBounds.right.toInt(),
            bubbleBottom = bubbleBounds.bottom.toInt(),
            onSelectAction = onSelectActionCommon,
            onCustomReactionClick = { event ->
                state.customReactionState.eventSink(CustomReactionEvent.ShowCustomReactionSheet(event))
            },
            onEmojiReactionClick = ::onEmojiReactionClick,
            // Правка форка (фаза 3): пикер эмодзи разворачивается внутри оверлея.
            customReactionState = state.customReactionState,
            emojiPickerRenderer = emojiPickerRenderer,
            onSelectEmoji = { uniqueId, emoji ->
                state.eventSink(MessagesEvent.ToggleReaction(emoji.unicode, uniqueId))
            },
            onDismiss = {
                messageActionsAnchor.bubbleBounds = null
                state.actionListState.eventSink(ActionListEvent.Clear)
                state.customReactionState.eventSink(CustomReactionEvent.DismissCustomReactionSheet)
            },
        )
    } else {
        // Откат на шторку снизу (нажали не по пузырю, координат нет). Ей блюр приходит
        // отдельным окном — тут гонки нет, шторка появляется анимацией снизу.
        val actionListBackdrop = rememberBlurredBackdrop(enabled = actionTarget != null)
        if (actionTarget != null && actionListBackdrop != null) {
            Popup {
                Image(
                    bitmap = actionListBackdrop,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        ActionListView(
            state = state.actionListState,
            onSelectAction = onSelectActionCommon,
            onCustomReactionClick = { event ->
                state.customReactionState.eventSink(CustomReactionEvent.ShowCustomReactionSheet(event))
            },
            onEmojiReactionClick = ::onEmojiReactionClick,
            onVerifiedUserSendFailureClick = { event ->
                state.timelineState.eventSink(TimelineEvent.ComputeVerifiedUserSendFailure(event))
            },
        )
    }

    // Привязанный оверлей рисует пикер эмодзи сам (инлайн). Шторку снизу оставляем только для
    // фолбэка (нажали не по пузырю, координат нет), иначе пикер показался бы дважды.
    if (actionTarget == null || bubbleBounds == null) {
        customReactionBottomSheet()
    }

    ReactionSummaryView(state = state.reactionSummaryState)
    ReadReceiptBottomSheet(
        state = state.readReceiptBottomSheetState,
        onUserDataClick = onUserDataClick,
    )
    ReinviteDialog(state = state)
    LinkView(
        onLinkValid = { link ->
            onLinkClick(link.url, false)
        },
        state = state.linkState,
    )

    SendFailureDialogView(
        sendFailureDialogState = state.timelineState.sendFailureDialogState,
        onDismiss = {
            state.timelineState.eventSink(TimelineEvent.HideSendFailureDialog)
        },
        onRetry = { event ->
            state.eventSink(
                MessagesEvent.HandleAction(
                    action = TimelineItemAction.RetrySending,
                    event = event,
                )
            )
            state.timelineState.eventSink(TimelineEvent.HideSendFailureDialog)
        },
        onRemoveMessage = { event ->
            state.eventSink(
                MessagesEvent.HandleAction(
                    action = TimelineItemAction.Redact,
                    event = event,
                )
            )
            state.timelineState.eventSink(TimelineEvent.HideSendFailureDialog)
        },
    )
}

@Composable
internal fun RowScope.MessagesMenuActions(
    displayThreads: Boolean,
    roomCallState: RoomCallState,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    onThreadsListClick: () -> Unit,
) {
    if (displayThreads) {
        Icon(
            modifier = Modifier.clickable(enabled = true, onClick = onThreadsListClick),
            imageVector = CompoundIcons.ThreadsSolid(),
            contentDescription = stringResource(CommonStrings.common_threads),
        )
        Spacer(Modifier.width(8.dp))
    }
    CallMenuItem(
        roomCallState = roomCallState,
        onJoinCallClick = onJoinCallClick,
    )
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun ReinviteDialog(state: MessagesState) {
    if (state.showReinvitePrompt) {
        ConfirmationDialog(
            title = stringResource(id = R.string.screen_room_invite_again_alert_title),
            content = stringResource(id = R.string.screen_room_invite_again_alert_message),
            cancelText = stringResource(id = CommonStrings.action_cancel),
            submitText = stringResource(id = CommonStrings.action_invite),
            onSubmitClick = { state.eventSink(MessagesEvent.InviteDialogDismissed(InviteDialogAction.Invite)) },
            onDismiss = { state.eventSink(MessagesEvent.InviteDialogDismissed(InviteDialogAction.Cancel)) }
        )
    }
}

@Composable
private fun MessagesViewContent(
    state: MessagesState,
    onContentClick: (TimelineItem.Event) -> Unit,
    onUserDataClick: (MatrixUser) -> Unit,
    onLinkClick: (Link, Boolean) -> Unit,
    onReactionClick: (key: String, TimelineItem.Event) -> Unit,
    onReactionLongClick: (key: String, TimelineItem.Event) -> Unit,
    onMoreReactionsClick: (TimelineItem.Event) -> Unit,
    onReadReceiptClick: (TimelineItem.Event) -> Unit,
    onMessageLongClick: (TimelineItem.Event) -> Unit,
    onGalleryItemClick: ((TimelineItem.Event, Int) -> Unit),
    onViewAllPinnedMessagesClick: () -> Unit,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    forceJumpToBottomVisibility: Boolean,
    onSwipeToReply: (TimelineItem.Event) -> Unit,
    modifier: Modifier = Modifier,
    knockRequestsBannerView: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        if (state.voiceMessageComposerState.showPermissionRationaleDialog) {
            VoiceMessagePermissionRationaleDialog(
                onContinue = {
                    state.voiceMessageComposerState.eventSink(VoiceMessageComposerEvent.AcceptPermissionRationale)
                },
                onDismiss = {
                    state.voiceMessageComposerState.eventSink(VoiceMessageComposerEvent.DismissPermissionsRationale)
                },
                appName = state.appName
            )
        }
        if (state.voiceMessageComposerState.showSendFailureDialog) {
            VoiceMessageSendingFailedDialog(
                onDismiss = { state.voiceMessageComposerState.eventSink(VoiceMessageComposerEvent.DismissSendFailureDialog) },
            )
        }

        Box {
            val scrollBehavior = PinnedMessagesBannerViewDefaults.rememberScrollBehavior(
                pinnedMessagesCount = (state.pinnedMessagesBannerState as? PinnedMessagesBannerState.Visible)?.pinnedMessagesCount() ?: 0,
            )
            val density = LocalDensity.current
            // Combined height of the banners overlaid above the timeline. Drives the floating
            // date badge offset so the badge sits below whichever banners are currently showing.
            var topBannersHeightDp by remember { mutableStateOf(0.dp) }

            // Larpgram: запрос показать лист стикер-пака (originalJson события + mxc стикера),
            // выставляется по тапу на стикер через LocalOpenStickerPack.
            var stickerPackRequest by remember { mutableStateOf<Pair<String?, String?>?>(null) }

            // Правка форка: загрузчик медиа для кружочков. Кладём его прямо здесь, вокруг
            // таймлайна: кружочек рисуется глубоко внутри него, и протаскивать загрузчик
            // параметрами пришлось бы через апстримовские компоненты, то есть ловить
            // конфликты при каждом ребейзе.
            CompositionLocalProvider(
                LocalCircleMediaLoader provides state.circleMediaLoader,
                LocalOpenStickerPack provides { originalJson, stickerUrl ->
                    stickerPackRequest = originalJson to stickerUrl
                },
            ) {
                TimelineView(
                    state = state.timelineState,
                    timelineProtectionState = state.timelineProtectionState,
                    onUserDataClick = onUserDataClick,
                    onLinkClick = { link -> onLinkClick(link, false) },
                    onContentClick = onContentClick,
                    onGalleryItemClick = onGalleryItemClick,
                    onMessageLongClick = onMessageLongClick,
                    onSwipeToReply = onSwipeToReply,
                    onReactionClick = onReactionClick,
                    onReactionLongClick = onReactionLongClick,
                    onMoreReactionsClick = onMoreReactionsClick,
                    onReadReceiptClick = onReadReceiptClick,
                    onJoinCallClick = onJoinCallClick,
                    forceJumpToBottomVisibility = forceJumpToBottomVisibility,
                    nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                    floatingDateTopOffset = topBannersHeightDp,
                )
            }

            // Larpgram: лист стикер-пака по тапу на стикер.
            val packRequest = stickerPackRequest
            val packSource = state.imagePackSource
            if (packRequest != null && packSource != null) {
                StickerPackSheet(
                    originalJson = packRequest.first,
                    stickerUrl = packRequest.second,
                    imagePackSource = packSource,
                    onDismiss = { stickerPackRequest = null },
                )
            }

            if (state.timelineState.timelineMode !is Timeline.Mode.Thread) {
                Column(
                    modifier = Modifier
                        .onSizeChanged { topBannersHeightDp = with(density) { it.height.toDp() } }
                        // Правка форка: плашки — под плавающей шапкой.
                        .padding(top = LocalChatTopOverlayHeight.current),
                ) {
                    AnimatedVisibility(
                        visible = state.pinnedMessagesBannerState is PinnedMessagesBannerState.Visible && scrollBehavior.isVisible,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        fun focusOnPinnedEvent(eventId: EventId) {
                            state.timelineState.eventSink(
                                TimelineEvent.FocusOnEvent(eventId = eventId, debounce = FOCUS_ON_PINNED_EVENT_DEBOUNCE_DURATION_IN_MILLIS.milliseconds)
                            )
                        }
                        PinnedMessagesBannerView(
                            state = state.pinnedMessagesBannerState,
                            onClick = ::focusOnPinnedEvent,
                            onViewAllClick = onViewAllPinnedMessagesClick,
                        )
                    }
                    if (state.showLiveLocationShareBanner) {
                        LiveLocationSharingBanner(
                            onClick = { state.eventSink(MessagesEvent.ShowLiveLocationShare) },
                            onStopClick = { state.eventSink(MessagesEvent.StopLiveLocationShare) }
                        )
                    }
                }
            }

            Box(modifier = Modifier.padding(top = LocalChatTopOverlayHeight.current)) {
                knockRequestsBannerView()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesViewComposerBottomSheetContents(
    state: MessagesState,
    onRoomSuccessorClick: (RoomId) -> Unit,
    onLinkClick: (String, Boolean) -> Unit,
    mediaPanel: TgMediaPanelController,
    emojiKeyboard: (@Composable (Modifier, (String) -> Unit) -> Unit)?,
) {
    val contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()

    // Правка форка: стикеры и гифки — вкладки панели Telegram под полем ввода (TgMediaPanel).
    val stickerPickerState = state.stickerPickerState
    val gifPickerState = state.gifPickerState
    val localView = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    // Панель живёт дольше вкладки, поэтому при открытии GIF просим перечитать данные. Иначе
    // список недавних гифок остаётся таким, каким был при первом показе.
    LaunchedEffect(mediaPanel.isVisible, mediaPanel.tab) {
        if (mediaPanel.isVisible && mediaPanel.tab == TgMediaPanelTab.Gif) {
            gifPickerState?.eventSink(GifPickerEvents.Retry)
        }
    }
    val markdownState = (state.composerState.textEditorState as? TextEditorState.Markdown)?.state

    // Правка форка: сообщение о неудачной отправке стикера живёт СНАРУЖИ шторки. Внутри
    // его бы никто не увидел: шторка закрывается в тот же момент, когда стикер уходит.
    if (stickerPickerState != null) {
        StickerSendErrorDialog(state = stickerPickerState)
    }

    // Правка форка: запись кружочка занимает весь экран поверх чата.
    state.circleRecorderState?.let { circleState ->
        CircleRecorderView(state = circleState)
    }

    when {
        state.successorRoom != null -> {
            SuccessorRoomBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                roomSuccessor = state.successorRoom,
                onRoomSuccessorClick = onRoomSuccessorClick
            )
        }
        // Правка форка (роумлесс, ф4 блок): заблокированный собеседник ЛС — вместо композера
        // полоса «Разблокировать». Приоритет выше canSendMessage (в ЛС писать технически можно).
        state.isUserBlocked -> {
            BlockedUserBar(
                onUnblock = { state.eventSink(MessagesEvent.UnblockUser) },
                modifier = Modifier.padding(contentPadding),
            )
        }
        state.userEventPermissions.canSendMessage -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding)
            ) {
                // Do not show the identity change if user is composing a Rich message or is seeing suggestion(s).
                if (state.composerState.suggestions.isEmpty() &&
                    state.composerState.textEditorState is TextEditorState.Markdown) {
                    IdentityChangeStateView(
                        state = state.identityChangeState,
                        onLinkClick = onLinkClick,
                    )
                }
                val verificationViolation = state.identityChangeState.roomMemberIdentityStateChanges.firstOrNull {
                    it.identityState == IdentityState.VerificationViolation
                }
                if (verificationViolation != null) {
                    DisabledComposerView(modifier = Modifier.fillMaxWidth())
                } else {
                    MessageComposerView(
                        state = state.composerState,
                        voiceMessageState = state.voiceMessageComposerState,
                        // Правка форка: вес без заполнения — панель эмодзи под полем меряется первой,
                        // иначе поле ввода (fillMaxSize внутри) забирает всю высоту шторки.
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        // Правка форка: кнопка слева открывает панель эмодзи/GIF/стикеров вместо
                        // клавиатуры, а при открытой панели возвращает клавиатуру.
                        onStickerClick = {
                            if (mediaPanel.isVisible) {
                                coroutineScope.launch {
                                    state.composerState.textEditorState.requestFocus()
                                    localView.showKeyboard()
                                }
                            } else {
                                mediaPanel.open()
                                localView.hideKeyboard()
                            }
                        },
                        isMediaPanelOpen = mediaPanel.isVisible,
                        // Правка форка: запись кружочка жестами, как в Telegram.
                        // Держишь — пишется, отпустил — улетело, свайп вверх фиксирует,
                        // свайп влево отменяет.
                        circleRecordGestures = state.circleRecorderState?.let { circleState ->
                            CircleRecordGestures(
                                onStart = {
                                    circleState.eventSink(CircleRecorderEvents.Open)
                                    circleState.eventSink(CircleRecorderEvents.StartRecording)
                                },
                                onLock = { circleState.eventSink(CircleRecorderEvents.LockRecording) },
                                onCancel = { circleState.eventSink(CircleRecorderEvents.CancelRecording) },
                                onFinish = { circleState.eventSink(CircleRecorderEvents.StopAndSend) },
                            )
                        },
                    )
                }
                TgMediaPanel(
                    controller = mediaPanel,
                    onBackspace = { markdownState?.deleteBeforeCursor() },
                    emojiContent = { modifier ->
                        emojiKeyboard?.invoke(modifier) { emoji -> markdownState?.insertAtCursor(emoji) }
                    },
                    gifContent = gifPickerState?.let { gifState ->
                        @Composable { modifier ->
                            GifPickerView(
                                state = gifState,
                                onGifClick = { gif -> gifState.eventSink(GifPickerEvents.SendGif(gif)) },
                                modifier = modifier,
                                fillHeight = true,
                                onSearchFocusChange = { mediaPanel.isSearchFocused = it },
                            )
                        }
                    },
                    stickerContent = stickerPickerState?.let { stickerState ->
                        @Composable { modifier ->
                            TgStickerPanel(
                                state = stickerState,
                                onStickerClick = { image -> stickerState.eventSink(StickerPickerEvents.SendSticker(image)) },
                                modifier = modifier,
                            )
                        }
                    },
                )
            }
        }
        state.isChannel -> {
            // Telegram-style channel subscriber bar: instead of the composer, a read-only
            // subscriber gets a mute/unmute pill. Comments live under each post; unsubscribe
            // lives in the channel profile.
            ChannelSubscriberBar(
                isMuted = state.isChannelMuted,
                onToggleMute = { state.eventSink(MessagesEvent.ToggleChannelMute) },
                modifier = Modifier.padding(contentPadding),
            )
        }
        else -> {
            CantSendMessageBanner(Modifier.padding(contentPadding))
        }
    }
}

@Composable
private fun ChannelSubscriberBar(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Telegram's channel bottom bar is a single centred pill. Tap toggles notifications;
        // the label and icon describe the action about to happen.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onToggleMute)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (isMuted) CompoundIcons.Notifications() else CompoundIcons.NotificationsOff(),
                contentDescription = null,
                tint = ElementTheme.colors.iconPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    id = if (isMuted) R.string.screen_channel_unmute else R.string.screen_channel_mute
                ),
                color = ElementTheme.colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

// Правка форка (роумлесс, ф4 блок): своя сторона TG-стены. Полоса вместо композера,
// тап снимает блок (unignoreUser).
@Composable
private fun BlockedUserBar(
    onUnblock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary)
            .clickable(onClick = onUnblock)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(id = R.string.screen_room_unblock_user),
            color = ElementTheme.colors.textCriticalPrimary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CantSendMessageBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.screen_room_timeline_no_permission_to_post),
            color = ElementTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun SuccessorRoomBanner(
    roomSuccessor: SuccessorRoom,
    onRoomSuccessorClick: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    ComposerAlertMolecule(
        avatar = null,
        content = stringResource(R.string.screen_room_timeline_tombstoned_room_message).toAnnotatedString(),
        onSubmitClick = { onRoomSuccessorClick(roomSuccessor.roomId) },
        modifier = modifier,
        submitText = stringResource(R.string.screen_room_timeline_tombstoned_room_action)
    )
}

@PreviewsDayNight
@Composable
internal fun MessagesViewPreview(@PreviewParameter(MessagesStatePreviewParam::class) state: MessagesState) = ElementPreview {
    MessagesView(
        state = state,
        onBackClick = {},
        onRoomDetailsClick = {},
        onEventContentClick = { _, _ -> false },
        onGalleryEventItemClick = { _, _, _ -> false },
        onUserDataClick = {},
        onLinkClick = { _, _ -> },
        onSendLocationClick = {},
        onCreatePollClick = {},
        onJoinCallClick = {},
        onViewAllPinnedMessagesClick = { },
        forceJumpToBottomVisibility = true,
        knockRequestsBannerView = {},
        customReactionBottomSheet = {},
        emojiPickerRenderer = NoOpEmojiPickerRenderer,
        onThreadsListClick = {},
    )
}

@Preview
@Composable
internal fun MessagesViewA11yPreview() = ElementPreview {
    val content = aTimelineItemTextContent(
        body = "A message content"
    )
    MessagesView(
        state = aMessagesState(
            roomName = "A DM with a very looong name",
            dmUserVerificationState = IdentityState.VerificationViolation,
            timelineState = aTimelineState(
                timelineItems = persistentListOf(
                    // 1 items with isMine = false
                    aTimelineItemEvent(
                        isMine = false,
                        content = content,
                        groupPosition = TimelineItemGroupPosition.None,
                        sendState = LocalEventSendState.Failed.Unknown("Message failed to send"),
                    ),
                    // A state event on top of it
                    aTimelineItemEvent(
                        isMine = false,
                        content = aTimelineItemStateEventContent(),
                        groupPosition = TimelineItemGroupPosition.None
                    ),
                    // 1 item with isMine = true
                    aTimelineItemEvent(
                        isMine = true,
                        content = content,
                        groupPosition = TimelineItemGroupPosition.None
                    ),
                    // A grouped event on top of it
                    aGroupedEvents(),
                    // A day separator
                    aTimelineItemDaySeparator(),
                ),
                // Render a focused event for an event with sender information displayed
                focusedEventIndex = 2,
            )
        ),
        onBackClick = {},
        onRoomDetailsClick = {},
        onEventContentClick = { _, _ -> false },
        onGalleryEventItemClick = { _, _, _ -> false },
        onUserDataClick = {},
        onLinkClick = { _, _ -> },
        onSendLocationClick = {},
        onCreatePollClick = {},
        onJoinCallClick = {},
        onViewAllPinnedMessagesClick = {},
        onThreadsListClick = {},
        forceJumpToBottomVisibility = true,
        knockRequestsBannerView = {},
        customReactionBottomSheet = {},
        emojiPickerRenderer = NoOpEmojiPickerRenderer,
    )
}
