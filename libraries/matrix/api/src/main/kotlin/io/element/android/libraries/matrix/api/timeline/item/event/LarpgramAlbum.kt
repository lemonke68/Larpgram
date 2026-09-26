/*
 * Правка форка: альбом в стиле Telegram, совместимый с любым клиентом Matrix.
 *
 * Раньше альбом уходил одним событием-галереей MSC4274. Её понимает только Element X с включённым
 * флагом, остальные клиенты (Element X на iOS, Element Web) показывали «неподдерживаемое событие»
 * (фидбек 2026-09-26). Теперь альбом — это несколько обычных m.image / m.video / m.file подряд,
 * как и устроено в самом Telegram (grouped_id). Чужой клиент видит их просто фотографиями подряд,
 * Larpgram собирает в мозаику.
 *
 * Метка альбома живёт в имени файла по той же причине, что и у кружочков
 * (см. [LARPGRAM_CIRCLE_FILENAME_PREFIX]): штатная отправка медиа не даёт добавить своё поле в content.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.timeline.item.event

import java.util.UUID

/** Часть альбома: [albumId] общий у всех частей, [index] с нуля, [count] — сколько частей всего. */
data class LarpgramAlbumPart(
    val albumId: String,
    val index: Int,
    val count: Int,
)

object LarpgramAlbum {
    /** Имя файла части: `larpgram-album-<id>-<номер с 1>-<всего>.<расширение>`. */
    const val FILENAME_PREFIX = "larpgram-album"

    private val filenameRegex = Regex("""^larpgram-album-([0-9a-f]{8})-(\d{1,3})-(\d{1,3})(\..*)?$""")

    fun newAlbumId(): String = UUID.randomUUID().toString().take(8)

    fun filename(albumId: String, index: Int, count: Int, extension: String?): String {
        val suffix = extension?.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        return "$FILENAME_PREFIX-$albumId-${index + 1}-$count$suffix"
    }

    fun parse(filename: String?): LarpgramAlbumPart? {
        val match = filename?.let(filenameRegex::matchEntire) ?: return null
        val number = match.groupValues[2].toInt()
        val count = match.groupValues[3].toInt()
        if (number < 1 || count < 2 || number > count) return null
        return LarpgramAlbumPart(
            albumId = match.groupValues[1],
            index = number - 1,
            count = count,
        )
    }
}
