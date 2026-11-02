/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.forward

import dev.zacsweers.metro.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Custom Matrix fields not exposed by the SDK; never changes server configuration. */
@Inject
class LarpgramSharingApi(private val http: OkHttpClient) {
    fun profileLink(base: String, token: String, userId: String): Boolean = readBoolean(
        request(base, token, "profile", userId, PROFILE_FIELD).get().build(),
        PROFILE_FIELD,
        absent = true,
    )

    fun setProfileLink(base: String, token: String, userId: String, allowed: Boolean) {
        val body = JsonObject(mapOf(PROFILE_FIELD to JsonPrimitive(allowed.toString()))).toString()
            .toRequestBody("application/json".toMediaType())
        // A string is supported by both string-only MSC4133 servers and general JSON implementations.
        http.newCall(request(base, token, "profile", userId, PROFILE_FIELD).put(body).build()).execute().use {
            if (!it.isSuccessful) throw IOException("Could not save forwarding privacy: HTTP ${it.code}")
        }
    }

    fun copyProtection(base: String, token: String, roomId: String): Boolean = readBoolean(
        request(base, token, "rooms", roomId, "state", PROTECTION_EVENT, "").get().build(),
        "enabled",
        absent = false,
    )

    private fun readBoolean(request: Request, field: String, absent: Boolean): Boolean = http.newCall(request).execute().use { response ->
        if (response.code == 404) return@use absent
        if (!response.isSuccessful) throw IOException("Could not read sharing policy: HTTP ${response.code}")
        val obj = Json.parseToJsonElement(response.body.string()).jsonObject
        val value = obj[field]?.jsonPrimitive ?: throw IOException("Missing sharing policy value")
        value.booleanOrNull ?: value.content.toBooleanStrictOrNull() ?: throw IOException("Invalid sharing policy value")
    }

    private fun request(base: String, token: String, vararg segments: String): Request.Builder {
        val url = base.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegments("_matrix/client/v3")
        segments.forEach(url::addPathSegment)
        return Request.Builder().url(url.build()).header("Authorization", "Bearer $token")
    }

    companion object {
        const val PROFILE_FIELD = "ru.mangokokos.larpgram.forwarding_profile_link"
        const val PROTECTION_EVENT = "ru.mangokokos.larpgram.content_protection"
    }
}
