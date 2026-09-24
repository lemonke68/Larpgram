/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.userprofile.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.features.userprofile.shared.tg.TgProfileCard
import io.element.android.features.userprofile.shared.tg.TgProfileInfoRow
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * TG-style profile info card, shared by the self profile, group-member profiles and DM
 * profiles. A Matrix profile carries only the id and an optional bio, so rows are: bio
 * ("About"/"О себе") when set, then the @handle (localpart of the Matrix id) over a
 * "Username" label. Tapping the handle copies the full id to the clipboard.
 */
@Composable
fun UserProfileInfoCard(
    userId: UserId,
    about: String?,
    onHandleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handle = userId.value.substringBefore(":")
    TgProfileCard(modifier = modifier) {
        // Bio row is shown only when set; TG order puts it above the handle.
        if (!about.isNullOrBlank()) {
            TgProfileInfoRow(
                value = about,
                label = stringResource(CommonStrings.larpgram_profile_about_label),
            )
        }
        TgProfileInfoRow(
            value = handle,
            label = stringResource(CommonStrings.common_username),
            onClick = onHandleClick,
        )
    }
}
