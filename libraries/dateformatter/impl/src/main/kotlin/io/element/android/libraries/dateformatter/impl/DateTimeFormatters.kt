/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.dateformatter.impl

import android.text.format.DateFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import android.icu.text.DateFormat as IcuDateFormat
import android.icu.text.SimpleDateFormat as IcuSimpleDateFormat
import android.icu.util.TimeZone as IcuTimeZone

class DateTimeFormatters(
    private val locale: Locale,
) {
    val onlyTimeFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }

    // Правка форка: всё, где есть название месяца или дня недели, форматируем через ICU, а не
    // java.time. Из-за core library desugaring (minSdk 24) java.time в приложении — это j$.time из
    // desugar_jdk_libs даже на новых Android, и он берёт именительный падеж: «14 сентябрь»,
    // «14 май» вместо «14 сентября», «14 мая». Юнит-тесты этого не ловят: там обычный JVM java.time.
    // Заодно "yyyy" вместо апстримового "YYYY" (год недели, врёт на стыке декабря и января).
    val dateWithMonthAndYearFormatter: LocalizedDateFormat by lazy {
        icuFormat(bestDateTimePattern("MMMM yyyy"))
    }

    val dateWithMonthFormatter: LocalizedDateFormat by lazy {
        icuFormat(bestDateTimePattern("d MMM"))
    }

    val dateWithDayFormatter: LocalizedDateFormat by lazy {
        icuFormat(bestDateTimePattern("EEEE"))
    }

    val dateWithYearFormatter: DateTimeFormatter by lazy {
        val pattern = bestDateTimePattern("dd.MM.yyyy")
        DateTimeFormatter.ofPattern(pattern, locale)
    }

    val dateWithFullFormatFormatter: LocalizedDateFormat by lazy {
        LocalizedDateFormat(IcuDateFormat.getDateInstance(IcuDateFormat.LONG, locale))
    }

    val dateWithFullFormatNoYearFormatter: LocalizedDateFormat by lazy {
        icuFormat(bestDateTimePattern("EEEE d MMMM"))
    }

    private fun icuFormat(pattern: String) = LocalizedDateFormat(IcuSimpleDateFormat(pattern, locale))

    private fun bestDateTimePattern(pattern: String): String {
        return DateFormat.getBestDateTimePattern(locale, pattern) ?: pattern
    }
}

/**
 * Правка форка: обёртка над ICU-форматом с тем же вызовом `format(LocalDateTime)`, что у java.time.
 * Время в LocalDateTime уже локальное, поэтому переводим его в миллисекунды как UTC и форматируем
 * тоже в UTC — поля даты остаются ровно те же. ICU-формат не потокобезопасен, отсюда synchronized.
 */
class LocalizedDateFormat(private val format: IcuDateFormat) {
    init {
        format.timeZone = IcuTimeZone.getTimeZone("UTC")
    }

    fun format(localDateTime: LocalDateTime): String {
        val millis = localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        return synchronized(format) { format.format(Date(millis)) }
    }
}
