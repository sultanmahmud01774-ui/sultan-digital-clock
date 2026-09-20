package com.example.util

import java.util.Calendar

object BengaliCalendarHelper {

    data class BanglaDate(
        val day: Int,
        val month: Int, // 1 = Boishakh ... 12 = Choitro
        val monthNameBn: String,
        val year: Int,
        val shortDate: String,      // e.g. "5/6/1433"
        val fullDateBn: String,     // e.g. "৫ আশ্বিন, ১৪৩৩"
        val fourDigits: String      // e.g. "0506" (Day 05, Month 06) for LED segment clock
    )

    private val BANGLA_MONTHS = listOf(
        "বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র", "আশ্বিন",
        "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ", "ফাল্গুন", "চৈত্র"
    )

    private val BANGLA_DIGITS = mapOf(
        '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
        '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
    )

    fun toBanglaDigits(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            sb.append(BANGLA_DIGITS[ch] ?: ch)
        }
        return sb.toString()
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    /**
     * Converts a Gregorian Calendar instance to Bangladesh revised Bengali Calendar.
     */
    fun getBanglaDate(calendar: Calendar = Calendar.getInstance()): BanglaDate {
        val gYear = calendar.get(Calendar.YEAR)
        val gMonth = calendar.get(Calendar.MONTH) + 1 // 1-indexed (1 = Jan, ..., 12 = Dec)
        val gDay = calendar.get(Calendar.DAY_OF_MONTH)

        val leap = isLeapYear(gYear)
        val falgunDays = if (leap) 30 else 29

        var bDay = 1
        var bMonth = 1
        var bYear = if (gMonth > 4 || (gMonth == 4 && gDay >= 14)) gYear - 593 else gYear - 594

        when (gMonth) {
            1 -> { // January
                if (gDay < 15) {
                    bMonth = 9 // Poush
                    bDay = gDay + 16
                } else {
                    bMonth = 10 // Magh
                    bDay = gDay - 14
                }
            }
            2 -> { // February
                if (gDay < 14) {
                    bMonth = 10 // Magh
                    bDay = gDay + 17
                } else {
                    bMonth = 11 // Falgun
                    bDay = gDay - 13
                }
            }
            3 -> { // March
                val falgunCutoff = if (leap) 15 else 14
                if (gDay <= (falgunDays - 15)) {
                    bMonth = 11 // Falgun
                    bDay = gDay + 15
                } else {
                    bMonth = 12 // Choitro
                    bDay = gDay - (if (leap) 15 else 14)
                }
            }
            4 -> { // April
                if (gDay < 14) {
                    bMonth = 12 // Choitro
                    bDay = gDay + 17
                } else {
                    bMonth = 1 // Boishakh
                    bDay = gDay - 13
                }
            }
            5 -> { // May
                if (gDay < 15) {
                    bMonth = 1 // Boishakh
                    bDay = gDay + 17
                } else {
                    bMonth = 2 // Joishtho
                    bDay = gDay - 14
                }
            }
            6 -> { // June
                if (gDay < 15) {
                    bMonth = 2 // Joishtho
                    bDay = gDay + 17
                } else {
                    bMonth = 3 // Ashar
                    bDay = gDay - 14
                }
            }
            7 -> { // July
                if (gDay < 16) {
                    bMonth = 3 // Ashar
                    bDay = gDay + 16
                } else {
                    bMonth = 4 // Srabon
                    bDay = gDay - 15
                }
            }
            8 -> { // August
                if (gDay < 16) {
                    bMonth = 4 // Srabon
                    bDay = gDay + 16
                } else {
                    bMonth = 5 // Bhadro
                    bDay = gDay - 15
                }
            }
            9 -> { // September
                if (gDay < 16) {
                    bMonth = 5 // Bhadro
                    bDay = gDay + 16
                } else {
                    bMonth = 6 // Ashwin
                    bDay = gDay - 15
                }
            }
            10 -> { // October
                if (gDay < 17) {
                    bMonth = 6 // Ashwin
                    bDay = gDay + 15
                } else {
                    bMonth = 7 // Kartik
                    bDay = gDay - 16
                }
            }
            11 -> { // November
                if (gDay < 16) {
                    bMonth = 7 // Kartik
                    bDay = gDay + 15
                } else {
                    bMonth = 8 // Ogrohayon
                    bDay = gDay - 15
                }
            }
            12 -> { // December
                if (gDay < 16) {
                    bMonth = 8 // Ogrohayon
                    bDay = gDay + 15
                } else {
                    bMonth = 9 // Poush
                    bDay = gDay - 15
                }
            }
        }

        val monthName = BANGLA_MONTHS.getOrElse(bMonth - 1) { "বৈশাখ" }
        val shortStr = "$bDay/$bMonth/$bYear"
        val fullStr = "${toBanglaDigits(bDay.toString())} $monthName, ${toBanglaDigits(bYear.toString())}"
        val fourDigitStr = String.format("%02d%02d", bDay, bMonth)

        return BanglaDate(
            day = bDay,
            month = bMonth,
            monthNameBn = monthName,
            year = bYear,
            shortDate = shortStr,
            fullDateBn = fullStr,
            fourDigits = fourDigitStr
        )
    }

    /**
     * Parse raw string from hardware status or format fallback
     */
    fun formatOrCompute(rawFromClock: String?): String {
        if (!rawFromClock.isNullOrBlank() && rawFromClock != "null" && rawFromClock.contains("/")) {
            return rawFromClock
        }
        return getBanglaDate().shortDate
    }
}
