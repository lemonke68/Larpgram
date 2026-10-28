/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Правка форка: вкладка «Профиль» Larpgram (редизайн V1 final) — профиль и корень настроек на одном
 * экране: аватар-«капля», музыка профиля, карточка Matrix ID, стеклянные карточки настроек.
 * Заменяет элементовский `PreferencesRootView`, который оставлен как в апстриме (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.LarpgramType
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.compound.tokens.larpgramColors
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.userstatus.UserStatusView
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import io.element.android.libraries.designsystem.components.async.AsyncActionIndicator
import io.element.android.libraries.designsystem.components.async.AsyncIndicator
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.TgOwnAvatar
import io.element.android.libraries.designsystem.components.glass.TgOwnAvatarShape
import io.element.android.libraries.designsystem.components.glass.tgGlowBackground
import io.element.android.libraries.designsystem.preview.ElementPreviewDark
import io.element.android.libraries.designsystem.preview.ElementPreviewLight
import io.element.android.libraries.designsystem.preview.PreviewWithLargeHeight
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.emoji.api.picker.EmojiPickerRenderer
import io.element.android.libraries.emoji.api.picker.NoOpEmojiPickerRenderer
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.model.getBestName
import io.element.android.libraries.matrix.ui.saved.SavedMessagesAvatar
import io.element.android.libraries.qrcode.QrCodeImage
import io.element.android.libraries.ui.strings.CommonStrings
import timber.log.Timber

/**
 * Extra data of the Profile tab that the settings presenter does not provide; filled by
 * [PreferencesRootNode].
 *
 * @property about the public "about" text, null when not set.
 * @property isKeyBackupMissing recovery (key backup) is not set up: «Нет копии» next to the encryption row.
 * @property isSessionUnverified this session is not verified: a warning row, nothing when verified.
 * @property profileLink matrix.to link of the user for the profile QR code.
 * @property profileMusic the profile music chip (MSC4133 field), null in previews.
 */
data class TgProfileExtras(
    val about: String? = null,
    val isKeyBackupMissing: Boolean = false,
    val isSessionUnverified: Boolean = false,
    val profileLink: String? = null,
    val profileMusic: (@Composable (Modifier) -> Unit)? = null,
)

@Composable
fun TgPreferencesRootView(
    state: PreferencesRootState,
    emojiPickerRenderer: EmojiPickerRenderer,
    onBackClick: (() -> Unit)?,
    @Suppress("UNUSED_PARAMETER") onAddAccountClick: () -> Unit,
    // Правка форка (Larpgram): Element v26.10 switches accounts via navigation (SwitchToSession event removed).
    @Suppress("UNUSED_PARAMETER") onSwitchToSession: (SessionId) -> Unit,
    onOpenCategory: (SettingsCategory) -> Unit,
    onOpenUserProfile: (MatrixUser) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenRageShake: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSavedMessages: () -> Unit = {},
    onOpenEncryption: () -> Unit = {},
    extras: TgProfileExtras = TgProfileExtras(),
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)
    val context = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    val onEditProfile = { onOpenUserProfile(state.myUser) }

    Box(modifier = modifier.fillMaxSize().tgGlowBackground()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            ProfileTopButtons(
                onBackClick = onBackClick,
                onQrClick = { showQr = true },
                onEditProfile = onEditProfile,
                hasQr = extras.profileLink != null,
            )
            ProfileHeader(
                matrixUser = state.myUser,
                onEditProfile = onEditProfile,
                modifier = Modifier.offset(y = (-14).dp),
            )
            extras.profileMusic?.let { music ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    music(Modifier.widthIn(max = 320.dp).padding(horizontal = 24.dp))
                }
            }
            // The Matrix ID card is the only place in the app that shows the server name.
            ProfileCard(modifier = Modifier.padding(top = 12.dp)) {
                InfoRow(
                    value = state.myUser.userId.value,
                    label = stringResource(R.string.larpgram_profile_matrix_id),
                    trailingIcon = CompoundIcons.Copy(),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Matrix ID", state.myUser.userId.value))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, R.string.larpgram_profile_copied, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                CardDivider()
                if (extras.about.isNullOrBlank()) {
                    InfoRow(
                        value = stringResource(R.string.larpgram_profile_about_empty),
                        label = stringResource(R.string.larpgram_profile_about),
                        valueColor = ElementTheme.colors.textSecondary,
                        onClick = onEditProfile,
                    )
                } else {
                    InfoRow(
                        value = extras.about,
                        label = stringResource(R.string.larpgram_profile_about),
                        onClick = onEditProfile,
                    )
                }
            }
            if (state.userStatusState != null) {
                ProfileCard(modifier = Modifier.padding(top = 10.dp)) {
                    UserStatusView(
                        state = state.userStatusState,
                        emojiPickerRenderer = emojiPickerRenderer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            SettingsCard(
                extras = extras,
                onOpenSavedMessages = onOpenSavedMessages,
                onOpenCategory = onOpenCategory,
                onOpenEncryption = onOpenEncryption,
            )
            AppInfoCard(
                state = state,
                onOpenAbout = onOpenAbout,
                onOpenRageShake = onOpenRageShake,
                onOpenLabs = onOpenLabs,
                onOpenDeveloperSettings = onOpenDeveloperSettings,
                onOpenAdvancedSettings = onOpenAdvancedSettings,
            )
            Footer(
                version = state.version,
                onClick = if (!state.showDeveloperSettings) {
                    { state.eventSink(PreferencesRootEvent.OnVersionInfoClick) }
                } else {
                    null
                }
            )
            // The tab lies under the floating bottom bar: room for the last rows to scroll above it.
            if (onBackClick == null) {
                Spacer(modifier = Modifier.height(96.dp))
            }
            Spacer(Modifier.navigationBarsPadding())
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        state.userStatusState?.let {
            UserStatusUpdateIndicator(it.updateStatusAction)
        }
    }
    if (showQr && extras.profileLink != null) {
        ProfileQrDialog(link = extras.profileLink, user = state.myUser, onDismiss = { showQr = false })
    }
}

/** A translucent profile card (`glass.card`, radius 20, 1 dp light border). */
@Composable
private fun ProfileCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = larpgramColors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .glassSurface(shape, colors.glassCard)
            .padding(vertical = 2.dp),
        content = content,
    )
}

@Composable
private fun Modifier.glassSurface(shape: Shape, fill: Color): Modifier {
    val colors = larpgramColors
    // Over the soft glow a blur would change nothing visible, so the profile cards are plain
    // translucent fills; the bottom bar above them still blurs the content.
    return this
        .clip(shape)
        .background(fill)
        .border(1.dp, Brush.verticalGradient(listOf(colors.glassTopHighlight, colors.glassBorder.copy(alpha = colors.glassBorder.alpha * 0.6f))), shape)
}

@Composable
private fun CardDivider() {
    HorizontalDivider(color = ElementTheme.colors.separatorPrimary)
}

@Composable
private fun ProfileTopButtons(
    onBackClick: (() -> Unit)?,
    onQrClick: () -> Unit,
    onEditProfile: () -> Unit,
    hasQr: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBackClick != null) {
            RoundGlassButton(icon = CompoundIcons.ArrowLeft(), contentDescription = stringResource(CommonStrings.action_back), onClick = onBackClick)
        }
        Spacer(Modifier.weight(1f))
        if (hasQr) {
            RoundGlassButton(icon = CompoundIcons.QrCode(), contentDescription = stringResource(R.string.larpgram_profile_qr_title), onClick = onQrClick)
        }
        Box {
            RoundGlassButton(icon = CompoundIcons.OverflowVertical(), contentDescription = null, onClick = { showMenu = true })
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.larpgram_profile_edit)) },
                    leadingIcon = { Icon(imageVector = CompoundIcons.Edit(), contentDescription = null, tint = ElementTheme.colors.iconSecondary) },
                    onClick = {
                        showMenu = false
                        onEditProfile()
                    },
                )
            }
        }
    }
}

@Composable
private fun RoundGlassButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .glassSurface(CircleShape, larpgramColors.glassSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = ElementTheme.colors.iconPrimary, modifier = Modifier.size(22.dp))
    }
}

/** Own avatar 108 dp in the blob shape with an accent camera badge, name in Unbounded, «@nick · в сети». */
@Composable
private fun ProfileHeader(
    matrixUser: MatrixUser,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = larpgramColors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.clickable(onClick = onEditProfile)) {
            TgOwnAvatar(
                avatarData = matrixUser.getAvatarData(size = AvatarSize.UserHeader),
                size = 108.dp,
                contentDescription = stringResource(CommonStrings.a11y_user_avatar),
                modifier = Modifier.shadow(16.dp, TgOwnAvatarShape, ambientColor = colors.glassShadow, spotColor = colors.accent.copy(alpha = 0.35f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.background)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(16.dp),
                    imageVector = CompoundIcons.TakePhoto(),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = matrixUser.getBestName(),
            style = LarpgramType.profileName,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            // The localpart only: the server name is shown in the Matrix ID card.
            text = "@" + matrixUser.userId.extractedDisplayName + " · " + stringResource(CommonStrings.larpgram_presence_online),
            style = LarpgramType.chatRowPreview.copy(fontWeight = FontWeight.SemiBold),
            color = ElementTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun InfoRow(
    value: String,
    label: String,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null,
    valueColor: Color = ElementTheme.colors.textPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = value, style = LarpgramType.listItem, color = valueColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(text = label, style = LarpgramType.caption, color = ElementTheme.colors.textSecondary)
        }
        if (trailingIcon != null) {
            Icon(imageVector = trailingIcon, contentDescription = null, tint = ElementTheme.colors.iconSecondary, modifier = Modifier.size(22.dp))
        }
    }
}

/** Settings row (50 dp, 30 dp coloured rounded-square icon), the V1 list of the Profile tab. */
@Composable
private fun SettingsRow(
    title: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    value: String? = null,
    valueColor: Color = ElementTheme.colors.textSecondary,
    titleColor: Color = ElementTheme.colors.textPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(color, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(
            text = title,
            style = LarpgramType.listItem,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(text = value, style = LarpgramType.listItem.copy(fontSize = LarpgramType.folderChip.fontSize), color = valueColor, maxLines = 1)
        }
        Icon(
            imageVector = CompoundIcons.ChevronRight(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** `tokens.json` → `settingsIconColors`. */
private object TgProfileIconColors {
    val Saved = Color(0xFF5B8DEF)
    val Account = Color(0xFF7C5CE6)
    val ChatSettings = Color(0xFFF0A030)
    val Privacy = Color(0xFF3DB46D)
    val Encryption = Color(0xFF9B6BF0)
    val Notifications = Color(0xFFEF5A5A)
    val Data = Color(0xFF2BA8C9)
    val Devices = Color(0xFF2DB7A3)
}

private fun SettingsCategory.v1Color(): Color = when (this) {
    SettingsCategory.Account -> TgProfileIconColors.Account
    SettingsCategory.Chats -> TgProfileIconColors.ChatSettings
    SettingsCategory.Privacy -> TgProfileIconColors.Privacy
    SettingsCategory.Notifications -> TgProfileIconColors.Notifications
    SettingsCategory.Data -> TgProfileIconColors.Data
    SettingsCategory.Devices -> TgProfileIconColors.Devices
    else -> color
}

@Composable
private fun SettingsCard(
    extras: TgProfileExtras,
    onOpenSavedMessages: () -> Unit,
    onOpenCategory: (SettingsCategory) -> Unit,
    onOpenEncryption: () -> Unit,
) {
    val colors = larpgramColors
    val categories = SettingsCategory.entries.filter { it.isAvailable && it != SettingsCategory.Language }
    ProfileCard(modifier = Modifier.padding(top = 10.dp)) {
        // Shown only when this session is NOT verified; a verified session shows nothing.
        if (extras.isSessionUnverified) {
            SettingsRow(
                title = stringResource(R.string.larpgram_session_unverified_title),
                color = colors.danger,
                icon = CompoundIcons.ErrorSolid(),
                titleColor = colors.danger,
                onClick = onOpenEncryption,
            )
            CardDivider()
        }
        SettingsRow(
            title = stringResource(CommonStrings.larpgram_saved_messages),
            color = TgProfileIconColors.Saved,
            icon = SavedMessagesAvatar.icon,
            onClick = onOpenSavedMessages,
        )
        categories.forEach { category ->
            CardDivider()
            SettingsRow(
                title = stringResource(category.titleRes),
                color = category.v1Color(),
                icon = category.icon,
                onClick = { onOpenCategory(category) },
            )
            // «Шифрование и ключи» goes right after «Конфиденциальность», as in the mockup.
            if (category == SettingsCategory.Privacy) {
                CardDivider()
                SettingsRow(
                    title = stringResource(R.string.larpgram_settings_encryption_title),
                    color = TgProfileIconColors.Encryption,
                    icon = CompoundIcons.Shield(),
                    value = if (extras.isKeyBackupMissing) stringResource(R.string.larpgram_settings_no_backup) else null,
                    valueColor = colors.warning,
                    onClick = onOpenEncryption,
                )
            }
        }
    }
}

@Composable
private fun AppInfoCard(
    state: PreferencesRootState,
    onOpenAbout: () -> Unit,
    onOpenRageShake: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
) {
    val context = LocalContext.current
    ProfileCard(modifier = Modifier.padding(top = 10.dp)) {
        if (SettingsCategory.Language.isAvailable) {
            SettingsRow(
                title = stringResource(SettingsCategory.Language.titleRes),
                color = SettingsCategory.Language.color,
                icon = SettingsCategory.Language.icon,
                onClick = { openAppLanguageSettings(context) },
            )
            CardDivider()
        }
        SettingsRow(
            title = stringResource(id = CommonStrings.common_about),
            color = TgSettingsColors.Blue,
            icon = CompoundIcons.Info(),
            onClick = onOpenAbout,
        )
        CardDivider()
        // Полный экран «Дополнительно» (dev-режим, live-location и пр. — то, что не легло в категории).
        SettingsRow(
            title = stringResource(id = CommonStrings.common_advanced_settings),
            color = TgSettingsColors.Gray,
            icon = CompoundIcons.Settings(),
            onClick = onOpenAdvancedSettings,
        )
        if (state.preferencesAccountState.canReportBug) {
            CardDivider()
            SettingsRow(
                title = stringResource(id = CommonStrings.common_report_a_problem),
                color = TgSettingsColors.Orange,
                icon = CompoundIcons.ChatProblem(),
                onClick = onOpenRageShake,
            )
        }
        if (state.showLabsItem) {
            CardDivider()
            SettingsRow(
                title = stringResource(id = R.string.screen_labs_title),
                color = TgSettingsColors.Purple,
                icon = CompoundIcons.Labs(),
                onClick = onOpenLabs,
            )
        }
        // В конце, чтобы случайный 8-кратный тап по версии ничего не ломал.
        if (state.showDeveloperSettings) {
            CardDivider()
            SettingsRow(
                title = stringResource(id = CommonStrings.common_developer_options),
                color = TgSettingsColors.Gray,
                icon = CompoundIcons.Code(),
                onClick = onOpenDeveloperSettings,
            )
        }
    }
}

/** Profile QR: the matrix.to link of the user, opens the profile in any Matrix app. */
@Composable
private fun ProfileQrDialog(
    link: String,
    user: MatrixUser,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(ElementTheme.colors.bgCanvasDefault)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = user.getBestName(), style = LarpgramType.screenTitle, color = ElementTheme.colors.textPrimary)
            Spacer(Modifier.height(16.dp))
            // QR-код всегда на белом: тёмный фон камеры читают хуже.
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp)
            ) {
                QrCodeImage(data = link, modifier = Modifier.size(220.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.larpgram_profile_qr_hint),
                style = LarpgramType.caption,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Правка форка: системный выбор языка приложения (Android 13+), как «Язык» в TG. */
private fun openAppLanguageSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(intent) }
        .onFailure { Timber.w(it, "Не открылся системный выбор языка") }
}

@Composable
private fun BoxScope.UserStatusUpdateIndicator(updateStatusAction: AsyncAction<Unit>) {
    AsyncActionIndicator(
        asyncAction = updateStatusAction,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding(),
        loading = { AsyncIndicator.Loading(text = stringResource(CommonStrings.common_saving)) },
        failure = { _ -> AsyncIndicator.Failure(text = stringResource(CommonStrings.common_failed)) },
    )
}

@Composable
private fun ColumnScope.Footer(
    version: String,
    onClick: (() -> Unit)?,
) {
    Text(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        textAlign = TextAlign.Center,
        text = version,
        style = ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textSecondary,
    )
}

@PreviewWithLargeHeight
@Composable
internal fun TgPreferencesRootViewLightPreview(@PreviewParameter(PreferencesRootStatePreviewParam::class) state: PreferencesRootState) =
    ElementPreviewLight(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@PreviewWithLargeHeight
@Composable
internal fun TgPreferencesRootViewDarkPreview(@PreviewParameter(PreferencesRootStatePreviewParam::class) state: PreferencesRootState) =
    ElementPreviewDark(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@ExcludeFromCoverage
@Composable
private fun ContentToPreview(state: PreferencesRootState) {
    TgPreferencesRootView(
        state = state,
        emojiPickerRenderer = NoOpEmojiPickerRenderer,
        onBackClick = null,
        onAddAccountClick = {},
        onSwitchToSession = {},
        onOpenCategory = {},
        onOpenUserProfile = {},
        onOpenAbout = {},
        onOpenRageShake = {},
        onOpenLabs = {},
        onOpenDeveloperSettings = {},
        onOpenAdvancedSettings = {},
        extras = TgProfileExtras(about = "Мастер по реквизиту", isKeyBackupMissing = true, profileLink = "https://matrix.to/#/@mila:example.org"),
    )
}
