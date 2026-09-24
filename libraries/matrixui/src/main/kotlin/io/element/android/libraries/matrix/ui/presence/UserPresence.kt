/*
 * Правка форка: присутствие пользователя («в сети», «был(а) в 14:20») для шапки чата и профилей.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.ui.presence

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.ui.strings.CommonPlurals
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * Присутствие пользователя.
 *
 * @param isOnline пользователь сейчас активен.
 * @param lastActiveAtMillis когда был активен в последний раз (по часам телефона), если известно.
 */
@Immutable
data class UserPresence(
    val isOnline: Boolean,
    val lastActiveAtMillis: Long?,
)

/**
 * rust-SDK presence не отдаёт, поэтому спрашиваем Synapse напрямую:
 * `GET /_matrix/client/v3/presence/{userId}/status` с токеном сессии.
 */
@Inject
class UserPresenceFetcher(
    private val matrixClient: MatrixClient,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: CoroutineDispatchers,
) {
    /** null — сервер не ответил или не делится присутствием; подпись тогда «был(а) недавно». */
    suspend fun fetch(userId: UserId): UserPresence? {
        val token = matrixClient.getAccessToken().getOrNull() ?: return null
        val base = matrixClient.homeserverUrl.trimEnd('/')
        val encodedUserId = URLEncoder.encode(userId.value, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("$base/_matrix/client/v3/presence/$encodedUserId/status")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return withContext(dispatchers.io) {
            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = JSONObject(response.body.string())
                    val lastActiveAgo = json.optLong("last_active_ago", -1L).takeIf { it >= 0 }
                    UserPresence(
                        isOnline = json.optBoolean("currently_active", false) || json.optString("presence") == "online",
                        lastActiveAtMillis = lastActiveAgo?.let { System.currentTimeMillis() - it },
                    )
                }
            }.getOrElse {
                Timber.w(it, "Не получилось узнать presence $userId")
                null
            }
        }
    }
}

/** Presence, пока экран открыт; опрос раз в 30 секунд. */
@Composable
fun UserPresenceFetcher.rememberPresence(userId: UserId?): UserPresence? {
    val presence by produceState<UserPresence?>(initialValue = null, userId) {
        val id = userId ?: return@produceState
        while (true) {
            fetch(id)?.let { value = it }
            delay(PRESENCE_POLL_INTERVAL)
        }
    }
    return presence
}

private val PRESENCE_POLL_INTERVAL = 30.seconds

/** Подпись присутствия, как в Telegram. [isOnline] — рисовать цветом акцента. */
@Immutable
data class PresenceText(val text: String, val isOnline: Boolean)

@Composable
fun presenceText(presence: UserPresence?): PresenceText {
    if (presence?.isOnline == true) {
        return PresenceText(stringResource(CommonStrings.larpgram_presence_online), isOnline = true)
    }
    val lastActive = presence?.lastActiveAtMillis
        ?: return PresenceText(stringResource(CommonStrings.larpgram_presence_last_seen_recently), isOnline = false)
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val minutes = ((now - lastActive) / 60_000L).coerceAtLeast(0)
    val text = when {
        minutes < 1 -> stringResource(CommonStrings.larpgram_presence_last_seen_just_now)
        minutes < 60 -> pluralStringResource(CommonPlurals.larpgram_presence_last_seen_minutes, minutes.toInt(), minutes.toInt())
        else -> {
            val time = DateFormat.getTimeFormat(context).format(Date(lastActive))
            when (daysBetween(lastActive, now)) {
                0 -> stringResource(CommonStrings.larpgram_presence_last_seen_at, time)
                1 -> stringResource(CommonStrings.larpgram_presence_last_seen_yesterday, time)
                else -> stringResource(
                    CommonStrings.larpgram_presence_last_seen_date,
                    SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(lastActive)),
                )
            }
        }
    }
    return PresenceText(text, isOnline = false)
}

/** Сколько календарных дней между двумя моментами по местному времени. */
private fun daysBetween(from: Long, to: Long): Int {
    fun dayIndex(millis: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    // Округление, а не деление нацело: сутки с переводом часов длятся 23 или 25 часов.
    return Math.round((dayIndex(to) - dayIndex(from)) / 86_400_000.0).toInt()
}
