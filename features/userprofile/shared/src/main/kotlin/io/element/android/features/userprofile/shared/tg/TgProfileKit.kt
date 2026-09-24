/*
 * Правка форка: детали профиля в стиле Telegram 12 (ProfileActivity, ProfileActionsView,
 * SharedMediaLayout). Общие для профиля собеседника, группы, канала и своего профиля.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.userprofile.shared.tg

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.launch

/**
 * Размеры из исходников TG: `ProfileActionsView` (высота 74 минус отступы 12 и 8 = 54, поля 14,
 * зазор 7, скругление 16, подпись 11 жирным), имя 17.5 × 1.12, статус 13.5.
 */
object TgProfileDefaults {
    val sidePadding: Dp = 14.dp
    val actionHeight: Dp = 54.dp
    val actionGap: Dp = 7.dp
    val actionRadius: Dp = 16.dp
    val cardRadius: Dp = 16.dp
    val cardGap: Dp = 12.dp

    /** Фон кнопок действий и карточек: светлее страницы в тёмной теме, серее — в светлой. */
    val cardColor: Color
        @Composable get() = ElementTheme.colors.bgSubtleSecondary

    val pageColor: Color
        @Composable get() = ElementTheme.colors.bgCanvasDefault
}

/** Шапка: аватар, имя, подзаголовок («в сети», «был(а) …», «N участников»). */
@Composable
fun TgProfileHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    subtitleIsAccent: Boolean = false,
    onSubtitleClick: (() -> Unit)? = null,
    avatar: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        avatar()
        Spacer(Modifier.height(14.dp))
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = title,
            style = ElementTheme.typography.fontHeadingSmMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .then(if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier),
                text = subtitle,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = if (subtitleIsAccent) ElementTheme.colors.textActionAccent else ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Immutable
data class TgProfileAction(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** Ряд плиток действий: Чат / Звук / Звонок / Видео / Покинуть. Плитки делят ширину поровну. */
@Composable
fun TgProfileActions(
    actions: List<TgProfileAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TgProfileDefaults.sidePadding),
        horizontalArrangement = Arrangement.spacedBy(TgProfileDefaults.actionGap),
    ) {
        actions.forEach { action ->
            TgProfileActionButton(action = action)
        }
    }
}

@Composable
private fun RowScope.TgProfileActionButton(action: TgProfileAction) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(TgProfileDefaults.actionHeight)
            .clip(RoundedCornerShape(TgProfileDefaults.actionRadius))
            .background(TgProfileDefaults.cardColor)
            .clickable(onClick = action.onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = action.icon,
            contentDescription = null,
            tint = ElementTheme.colors.iconPrimary,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = action.title,
            style = ElementTheme.typography.fontBodyXsMedium.copy(fontWeight = FontWeight.Bold),
            color = ElementTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Карточка-блок на фоне страницы, как информационный блок профиля TG. */
@Composable
fun TgProfileCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TgProfileDefaults.sidePadding)
            .clip(RoundedCornerShape(TgProfileDefaults.cardRadius))
            .background(TgProfileDefaults.cardColor)
            .padding(vertical = 4.dp),
        content = content,
    )
}

/** Строка инфо-карточки: значение крупно, подпись («О себе», «Имя пользователя») мелко под ним. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TgProfileInfoRow(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = value,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

/**
 * Строка-действие в карточке (`TextCell` TG): иконка слева, текст с отступом 72, справа значение.
 * [accent] — синяя строка «Добавить участников».
 */
@Composable
fun TgProfileCardItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    accent: Boolean = false,
    destructive: Boolean = false,
) {
    val contentColor = when {
        destructive -> ElementTheme.colors.textCriticalPrimary
        accent -> ElementTheme.colors.textActionAccent
        else -> ElementTheme.colors.textPrimary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = icon,
            contentDescription = null,
            tint = when {
                destructive -> ElementTheme.colors.iconCriticalPrimary
                accent -> ElementTheme.colors.iconAccentPrimary
                else -> ElementTheme.colors.iconSecondary
            },
        )
        Spacer(Modifier.width(28.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * Вкладки общих медиа (Участники / Медиа / Файлы / Ссылки / Голосовые) — пилюля с выделенной
 * вкладкой, листается вбок, если не влезает.
 */
@Composable
fun TgProfileTabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TgProfileDefaults.pageColor)
            .padding(horizontal = TgProfileDefaults.sidePadding, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(TgProfileDefaults.cardColor)
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            titles.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                val background by animateColorAsState(
                    targetValue = if (selected) ElementTheme.colors.textPrimary.copy(alpha = 0.08f) else Color.Transparent,
                    label = "tabBackground",
                )
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(background)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = ElementTheme.typography.fontBodyLgMedium,
                        color = if (selected) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Пустая вкладка: «Здесь будут медиа из этого чата». */
@Composable
fun TgProfileEmptyTab(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

/**
 * Верхняя панель профиля: «назад», справа действия. Когда шапка уехала вверх, на панели
 * проявляются имя и статус (`profile_links_tab.jpg`).
 */
@Composable
fun TgProfileTopBar(
    title: String,
    subtitle: String?,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val titleAlpha by animateFloatAsState(if (showTitle) 1f else 0f, label = "topBarTitleAlpha")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TgProfileDefaults.pageColor)
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBackClick != null) {
            BackButton(onClick = onBackClick)
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .alpha(titleAlpha),
        ) {
            Text(
                text = title,
                style = ElementTheme.typography.fontBodyLgMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

@Immutable
data class TgProfileMenuItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

/** Меню ⋮ профиля: Поделиться, Заблокировать, Пожаловаться, Удалить чат / Покинуть группу. */
@Composable
fun TgProfileMenu(
    items: List<TgProfileMenuItem>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = CompoundIcons.OverflowVertical(),
                contentDescription = stringResource(CommonStrings.action_open_context_menu),
                tint = ElementTheme.colors.iconPrimary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = ElementTheme.colors.bgCanvasDefault,
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (item.destructive) ElementTheme.colors.iconCriticalPrimary else ElementTheme.colors.iconSecondary,
                        )
                    },
                    text = {
                        Text(
                            text = item.title,
                            style = ElementTheme.typography.fontBodyLgRegular,
                            color = if (item.destructive) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textPrimary,
                        )
                    },
                    onClick = {
                        // Меню закрываем до действия, иначе оно висит во время перехода.
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

/**
 * Тяга вниз раскрывает круглый аватар в квадрат во всю ширину (как в TG); тяга вверх сначала
 * сворачивает его, потом листает содержимое. [fraction]: 0 — круг, 1 — раскрыт.
 */
@Stable
class TgAvatarExpandState internal constructor(
    val connection: NestedScrollConnection,
    private val animatable: Animatable<Float, *>,
) {
    val fraction: Float get() = animatable.value
}

@Composable
fun rememberTgAvatarExpandState(enabled: Boolean = true): TgAvatarExpandState {
    val expandFraction = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val maxDragPx = remember(density, screenWidthDp) {
        with(density) { (screenWidthDp.dp - AvatarSize.UserHeader.dp).toPx() }.coerceAtLeast(1f)
    }
    val connection = remember(maxDragPx, enabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Палец идёт вверх: сначала сворачиваем аватар, потом листаем.
                val delta = available.y
                if (delta < 0f && expandFraction.value > 0f) {
                    val newFraction = (expandFraction.value + delta / maxDragPx).coerceIn(0f, 1f)
                    val consumed = (newFraction - expandFraction.value) * maxDragPx
                    scope.launch { expandFraction.snapTo(newFraction) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Список уже в самом верху, а палец тянет вниз: раскрываем аватар.
                val delta = available.y
                if (enabled && delta > 0f && source == NestedScrollSource.UserInput) {
                    val newFraction = (expandFraction.value + delta / maxDragPx).coerceIn(0f, 1f)
                    scope.launch { expandFraction.snapTo(newFraction) }
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Доводим до ближнего края, чтобы аватар не оставался наполовину раскрытым.
                if (expandFraction.value > 0f && expandFraction.value < 1f) {
                    val target = if (expandFraction.value > 0.5f) 1f else 0f
                    expandFraction.animateTo(target)
                }
                return Velocity.Zero
            }
        }
    }
    return remember(connection) { TgAvatarExpandState(connection, expandFraction) }
}

@PreviewsDayNight
@Composable
internal fun TgProfileKitPreview() = ElementPreview {
    Column {
        TgProfileTopBar(title = "Alice", subtitle = "в сети", showTitle = false, onBackClick = {})
        TgProfileActions(
            actions = listOf(
                TgProfileAction("Чат", CompoundIcons.ChatSolid(), {}),
                TgProfileAction("Звук", CompoundIcons.NotificationsSolid(), {}),
                TgProfileAction("Звонок", CompoundIcons.VoiceCallSolid(), {}),
                TgProfileAction("Видео", CompoundIcons.VideoCallSolid(), {}),
            )
        )
        Spacer(Modifier.height(TgProfileDefaults.cardGap))
        TgProfileCard {
            TgProfileInfoRow(value = "Люблю котов", label = "О себе")
            TgProfileInfoRow(value = "@alice", label = "Имя пользователя", onClick = {})
        }
        Spacer(Modifier.height(TgProfileDefaults.cardGap))
        TgProfileCard {
            TgProfileCardItem(title = "Участники", icon = CompoundIcons.User(), value = "12", onClick = {})
            TgProfileCardItem(title = "Добавить участников", icon = CompoundIcons.UserAdd(), accent = true, onClick = {})
        }
        TgProfileTabs(titles = listOf("Медиа", "Файлы", "Ссылки", "Голосовые"), selectedIndex = 0, onSelect = {})
        TgProfileEmptyTab(text = "Здесь будут медиа из этого чата.")
    }
}

/**
 * Строка списка внутри общей карточки (участники, файлы): скругляются только верх первой и низ
 * последней, между строками — тонкий разделитель от начала текста.
 */
@Composable
fun TgProfileCardSegment(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    dividerStart: Dp = 72.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val radius = TgProfileDefaults.cardRadius
    val shape = RoundedCornerShape(
        topStart = if (isFirst) radius else 0.dp,
        topEnd = if (isFirst) radius else 0.dp,
        bottomStart = if (isLast) radius else 0.dp,
        bottomEnd = if (isLast) radius else 0.dp,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TgProfileDefaults.sidePadding)
            .clip(shape)
            .background(TgProfileDefaults.cardColor),
    ) {
        content()
        if (!isLast) {
            HorizontalDivider(modifier = Modifier.padding(start = dividerStart))
        }
    }
}
