/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.model

import io.element.android.libraries.matrix.api.core.UserId

/**
 * Как ник TG в строке пользователя: `@ник` для людей со своего сервера и полный `@ник:сервер`
 * для чужих, чтобы не путать однофамильцев из федерации.
 */
fun UserId.tgHandle(ownServerName: String): String =
    if (domainName == ownServerName) "@$extractedDisplayName" else value
