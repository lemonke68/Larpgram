/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.theme.Theme
import io.element.android.compound.theme.mapToTheme
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.DEFAULT_BUBBLE_CORNER_RADIUS_DP
import io.element.android.libraries.preferences.api.store.DEFAULT_MESSAGE_TEXT_SIZE_SP

val LocalBuildMeta = staticCompositionLocalOf {
    BuildMeta(
        isDebuggable = true,
        buildType = BuildType.DEBUG,
        applicationName = "MyApp",
        productionApplicationName = "MyAppProd",
        desktopApplicationName = "MyAppDesktop",
        applicationId = "AppId",
        isEnterpriseBuild = false,
        lowPrivacyLoggingEnabled = false,
        versionName = "aVersion",
        versionCode = 123,
        gitRevision = "aRevision",
        gitBranchName = "aBranch",
        flavorDescription = "aFlavor",
        flavorShortDescription = "aFlavorShort",
    )
}

/**
 * Theme to use for all the regular screens of the application.
 * Will manage the light / dark theme based on the user preference.
 * Will also ensure that the system is applying the correct global theme
 * to the application, especially when the system is light and the application
 * is forced to use dark theme.
 */
@Composable
fun ElementThemeApp(
    appPreferencesStore: AppPreferencesStore,
    featureFlagService: FeatureFlagService,
    compoundLight: SemanticColors,
    compoundDark: SemanticColors,
    buildMeta: BuildMeta,
    content: @Composable () -> Unit,
) {
    val isBlackThemeAllowed by remember {
        featureFlagService.isFeatureEnabledFlow(FeatureFlags.AllowBlackTheme)
    }.collectAsState(initial = false)
    val theme by remember(isBlackThemeAllowed) {
        appPreferencesStore.getThemeFlow().mapToTheme(allowBlackTheme = isBlackThemeAllowed)
    }.collectAsState(initial = Theme.System)
    // Larpgram: chat appearance customization, provided app-wide for the timeline to read.
    val messageTextSizeSp by remember {
        appPreferencesStore.getMessageTextSizeSpFlow()
    }.collectAsState(initial = DEFAULT_MESSAGE_TEXT_SIZE_SP)
    val bubbleCornerRadiusDp by remember {
        appPreferencesStore.getBubbleCornerRadiusDpFlow()
    }.collectAsState(initial = DEFAULT_BUBBLE_CORNER_RADIUS_DP)
    val chatWallpaperId by remember {
        appPreferencesStore.getChatWallpaperIdFlow()
    }.collectAsState(initial = null)
    val chatWallpaperCustomColorArgb by remember {
        appPreferencesStore.getChatWallpaperCustomColorArgbFlow()
    }.collectAsState(initial = null)
    LaunchedEffect(theme) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                Theme.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                Theme.Light -> AppCompatDelegate.MODE_NIGHT_NO
                Theme.Dark, Theme.Black -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }
    CompositionLocalProvider(
        LocalBuildMeta provides buildMeta,
        LocalMessageTextScale provides ChatAppearanceDefaults.textScaleFor(messageTextSizeSp),
        LocalChatBubbleRadius provides ChatAppearanceDefaults.bubbleRadiusFor(bubbleCornerRadiusDp),
        LocalChatWallpaperId provides (chatWallpaperId ?: ChatWallpaperOption.DEFAULT.id),
        LocalChatWallpaperCustomColor provides chatWallpaperCustomColorArgb?.let { Color(it) },
    ) {
        ElementTheme(
            theme = theme,
            content = content,
            compoundLight = compoundLight,
            compoundDark = compoundDark,
        )
    }
}
