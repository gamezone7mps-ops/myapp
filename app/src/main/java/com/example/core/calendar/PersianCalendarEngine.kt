package com.example.core.calendar

import com.example.domain.model.AppLanguage
import com.example.domain.model.CalendarSystem
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.floor

data class CalendarDate(
  val year: Int,
  val month: Int, // 1..12
  val day: Int    // 1..31
)

data class ZonedDateTimeParts(
  val calendarSystem: CalendarSystem,
  val year: Int,
  val month: Int,
  val day: Int,
  val hour: Int,
  val minute: Int,
  val dayOfWeek: Int, // Calendar.SUNDAY..Calendar.SATURDAY
  val zoneId: String
)

/**
 * Pure Kotlin, API-24-compatible Jalali (Solar Hijri) <-> Gregorian conversion engine.
 *
 * Stored timestamps remain UTC epoch millis + explicit IANA TimeZone ID; conversion happens
 * strictly at the presentation/input boundary so switching display calendars never mutates data.
 */
object PersianCalendarEngine {

  private const val JALALI_1349_EPOCH_DAY = 79L
  private const val JALALI_ANCHOR_YEAR = 1349

  private val persianMonths = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
  )

  private val jalaliEnglishMonths = listOf(
    "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
    "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"
  )

  private val gregorianPersianMonths = listOf(
    "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
    "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
  )

  private val gregorianEnglishMonths = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  )

  private val persianWeekdays = mapOf(
    Calendar.SATURDAY to "شنبه",
    Calendar.SUNDAY to "یکشنبه",
    Calendar.MONDAY to "دوشنبه",
    Calendar.TUESDAY to "سه‌شنبه",
    Calendar.WEDNESDAY to "چهارشنبه",
    Calendar.THURSDAY to "پنج‌شنبه",
    Calendar.FRIDAY to "جمعه"
  )

  private val englishWeekdays = mapOf(
    Calendar.SATURDAY to "Saturday",
    Calendar.SUNDAY to "Sunday",
    Calendar.MONDAY to "Monday",
    Calendar.TUESDAY to "Tuesday",
    Calendar.WEDNESDAY to "Wednesday",
    Calendar.THURSDAY to "Thursday",
    Calendar.FRIDAY to "Friday"
  )

  fun isGregorianLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
  }

  fun gregorianMonthLength(year: Int, month: Int): Int {
    require(month in 1..12) { "Gregorian month must be in 1..12, got $month" }
    return when (month) {
      1, 3, 5, 7, 8, 10, 12 -> 31
      4, 6, 9, 11 -> 30
      2 -> if (isGregorianLeapYear(year)) 29 else 28
      else -> error("Unreachable")
    }
  }

  fun isJalaliLeapYear(year: Int): Boolean {
    val mod = Math.floorMod(year, 33)
    return mod == 1 || mod == 5 || mod == 9 || mod == 13 ||
      mod == 17 || mod == 22 || mod == 26 || mod == 30
  }

  fun jalaliMonthLength(year: Int, month: Int): Int {
    require(month in 1..12) { "Jalali month must be in 1..12, got $month" }
    return when (month) {
      in 1..6 -> 31
      in 7..11 -> 30
      12 -> if (isJalaliLeapYear(year)) 30 else 29
      else -> error("Unreachable")
    }
  }

  fun monthLength(year: Int, month: Int, calendarSystem: CalendarSystem): Int {
    return when (calendarSystem) {
      CalendarSystem.JALALI -> jalaliMonthLength(year, month)
      CalendarSystem.GREGORIAN -> gregorianMonthLength(year, month)
    }
  }

  fun gregorianToEpochDay(year: Int, month: Int, day: Int): Long {
    require(month in 1..12) { "Invalid Gregorian month: $month" }
    val maxDay = gregorianMonthLength(year, month)
    require(day in 1..maxDay) { "Invalid Gregorian day $day for $year-$month" }

    var y = year.toLong()
    var m = month.toLong()
    if (m <= 2L) {
      y -= 1L
      m += 12L
    }
    val days = 365L * y +
      floorDiv(y, 4L) -
      floorDiv(y, 100L) +
      floorDiv(y, 400L) +
      floorDiv(153L * (m - 3L) + 2L, 5L) +
      (day - 1L)
    return days - 719468L
  }

  fun epochDayToGregorian(epochDay: Long): CalendarDate {
    val z = epochDay + 719468L
    val era = if (z >= 0L) z / 146097L else (z - 146096L) / 146097L
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    var y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = mp + if (mp < 10L) 3L else -9L
    if (m <= 2L) y += 1L
    return CalendarDate(y.toInt(), m.toInt(), d.toInt())
  }

  fun jalaliToEpochDay(year: Int, month: Int, day: Int): Long {
    require(month in 1..12) { "Invalid Jalali month: $month" }
    val maxDay = jalaliMonthLength(year, month)
    require(day in 1..maxDay) { "Invalid Jalali day $day for $year-$month" }

    var totalDays = JALALI_1349_EPOCH_DAY
    if (year >= JALALI_ANCHOR_YEAR) {
      for (y in JALALI_ANCHOR_YEAR until year) {
        totalDays += if (isJalaliLeapYear(y)) 366L else 365L
      }
    } else {
      for (y in year until JALALI_ANCHOR_YEAR) {
        totalDays -= if (isJalaliLeapYear(y)) 366L else 365L
      }
    }

    for (m in 1 until month) {
      totalDays += jalaliMonthLength(year, m)
    }
    return totalDays + (day - 1L)
  }

  fun epochDayToJalali(epochDay: Long): CalendarDate {
    val approxYears = floor((epochDay - JALALI_1349_EPOCH_DAY) / 365.24219858156).toInt()
    var year = JALALI_ANCHOR_YEAR + approxYears

    var startOfYear = jalaliToEpochDay(year, 1, 1)
    while (startOfYear > epochDay) {
      year -= 1
      startOfYear = jalaliToEpochDay(year, 1, 1)
    }
    while (true) {
      val yearLen = if (isJalaliLeapYear(year)) 366L else 365L
      if (epochDay < startOfYear + yearLen) break
      startOfYear += yearLen
      year += 1
    }

    var dayOfYear = (epochDay - startOfYear).toInt()
    var month = 1
    while (month <= 12) {
      val len = jalaliMonthLength(year, month)
      if (dayOfYear < len) {
        return CalendarDate(year, month, dayOfYear + 1)
      }
      dayOfYear -= len
      month += 1
    }
    error("Unreachable Jalali epochDay conversion for $epochDay")
  }

  fun gregorianToJalali(year: Int, month: Int, day: Int): CalendarDate =
    epochDayToJalali(gregorianToEpochDay(year, month, day))

  fun jalaliToGregorian(year: Int, month: Int, day: Int): CalendarDate =
    epochDayToGregorian(jalaliToEpochDay(year, month, day))

  fun dateToEpochDay(date: CalendarDate, calendarSystem: CalendarSystem): Long =
    when (calendarSystem) {
      CalendarSystem.JALALI -> jalaliToEpochDay(date.year, date.month, date.day)
      CalendarSystem.GREGORIAN -> gregorianToEpochDay(date.year, date.month, date.day)
    }

  fun epochDayToDate(epochDay: Long, calendarSystem: CalendarSystem): CalendarDate =
    when (calendarSystem) {
      CalendarSystem.JALALI -> epochDayToJalali(epochDay)
      CalendarSystem.GREGORIAN -> epochDayToGregorian(epochDay)
    }

  fun utcMillisToZonedParts(
    utcMillis: Long,
    zoneId: String,
    calendarSystem: CalendarSystem
  ): ZonedDateTimeParts {
    val tz = TimeZone.getTimeZone(zoneId)
    val cal = GregorianCalendar(tz).apply { timeInMillis = utcMillis }
    val gYear = cal.get(Calendar.YEAR)
    val gMonth = cal.get(Calendar.MONTH) + 1
    val gDay = cal.get(Calendar.DAY_OF_MONTH)
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

    val date = when (calendarSystem) {
      CalendarSystem.JALALI -> gregorianToJalali(gYear, gMonth, gDay)
      CalendarSystem.GREGORIAN -> CalendarDate(gYear, gMonth, gDay)
    }
    return ZonedDateTimeParts(
      calendarSystem = calendarSystem,
      year = date.year,
      month = date.month,
      day = date.day,
      hour = hour,
      minute = minute,
      dayOfWeek = dayOfWeek,
      zoneId = tz.id
    )
  }

  fun zonedPartsToUtcMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    zoneId: String,
    calendarSystem: CalendarSystem
  ): Long {
    require(hour in 0..23) { "Hour must be in 0..23" }
    require(minute in 0..59) { "Minute must be in 0..59" }

    val gDate = when (calendarSystem) {
      CalendarSystem.JALALI -> jalaliToGregorian(year, month, day)
      CalendarSystem.GREGORIAN -> CalendarDate(year, month, day)
    }
    val tz = TimeZone.getTimeZone(zoneId)
    val cal = GregorianCalendar(tz).apply {
      isLenient = false
      set(Calendar.YEAR, gDate.year)
      set(Calendar.MONTH, gDate.month - 1)
      set(Calendar.DAY_OF_MONTH, gDate.day)
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }

  fun startOfDayUtcMillis(
    date: CalendarDate,
    zoneId: String,
    calendarSystem: CalendarSystem
  ): Long = zonedPartsToUtcMillis(
    year = date.year,
    month = date.month,
    day = date.day,
    hour = 0,
    minute = 0,
    zoneId = zoneId,
    calendarSystem = calendarSystem
  )

  fun endOfDayUtcMillis(
    date: CalendarDate,
    zoneId: String,
    calendarSystem: CalendarSystem
  ): Long {
    val start = startOfDayUtcMillis(date, zoneId, calendarSystem)
    val nextDayStart = addDays(start, 1, zoneId)
    return nextDayStart - 1L
  }

  fun addDays(utcMillis: Long, days: Int, zoneId: String): Long {
    val cal = GregorianCalendar(TimeZone.getTimeZone(zoneId)).apply {
      timeInMillis = utcMillis
      add(Calendar.DAY_OF_YEAR, days)
    }
    return cal.timeInMillis
  }

  fun addMonths(
    utcMillis: Long,
    months: Int,
    zoneId: String,
    calendarSystem: CalendarSystem
  ): Long {
    val parts = utcMillisToZonedParts(utcMillis, zoneId, calendarSystem)
    val totalMonths = (parts.year * 12) + (parts.month - 1) + months
    val newYear = floorDiv(totalMonths.toLong(), 12L).toInt()
    val newMonth = Math.floorMod(totalMonths, 12) + 1
    val clampedDay = parts.day.coerceAtMost(monthLength(newYear, newMonth, calendarSystem))
    return zonedPartsToUtcMillis(
      year = newYear,
      month = newMonth,
      day = clampedDay,
      hour = parts.hour,
      minute = parts.minute,
      zoneId = zoneId,
      calendarSystem = calendarSystem
    )
  }

  fun shiftMonth(year: Int, month: Int, deltaMonths: Int): Pair<Int, Int> {
    val total = (year * 12) + (month - 1) + deltaMonths
    val newYear = floorDiv(total.toLong(), 12L).toInt()
    val newMonth = Math.floorMod(total, 12) + 1
    return newYear to newMonth
  }

  fun shiftDateByDays(
    date: CalendarDate,
    deltaDays: Int,
    calendarSystem: CalendarSystem
  ): CalendarDate {
    val epochDay = dateToEpochDay(date, calendarSystem)
    return epochDayToDate(epochDay + deltaDays, calendarSystem)
  }

  /**
   * Returns 0..6 offset in the visual 7-column calendar grid.
   * Jalali week starts on Saturday (0 = Saturday .. 6 = Friday).
   * Gregorian week starts on Sunday (0 = Sunday .. 6 = Saturday).
   */
  fun dayOfWeekGridIndex(
    date: CalendarDate,
    zoneId: String,
    calendarSystem: CalendarSystem
  ): Int {
    val utc = zonedPartsToUtcMillis(date.year, date.month, date.day, 12, 0, zoneId, calendarSystem)
    val cal = GregorianCalendar(TimeZone.getTimeZone(zoneId)).apply { timeInMillis = utc }
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    return when (calendarSystem) {
      CalendarSystem.JALALI -> when (dow) {
        Calendar.SATURDAY -> 0
        Calendar.SUNDAY -> 1
        Calendar.MONDAY -> 2
        Calendar.TUESDAY -> 3
        Calendar.WEDNESDAY -> 4
        Calendar.THURSDAY -> 5
        Calendar.FRIDAY -> 6
        else -> 0
      }
      CalendarSystem.GREGORIAN -> when (dow) {
        Calendar.SUNDAY -> 0
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 0
      }
    }
  }

  fun weekdayShortHeaders(
    calendarSystem: CalendarSystem,
    language: AppLanguage
  ): List<String> {
    return when (calendarSystem) {
      CalendarSystem.JALALI ->
        if (language == AppLanguage.PERSIAN) listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
        else listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")
      CalendarSystem.GREGORIAN ->
        if (language == AppLanguage.PERSIAN) listOf("ی", "د", "س", "چ", "پ", "ج", "ش")
        else listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    }
  }

  fun weekDatesAround(
    selectedDate: CalendarDate,
    zoneId: String,
    calendarSystem: CalendarSystem
  ): List<CalendarDate> {
    val offset = dayOfWeekGridIndex(selectedDate, zoneId, calendarSystem)
    val startEpochDay = dateToEpochDay(selectedDate, calendarSystem) - offset
    return (0..6).map { i -> epochDayToDate(startEpochDay + i, calendarSystem) }
  }

  fun monthName(month: Int, calendarSystem: CalendarSystem, language: AppLanguage): String {
    require(month in 1..12) { "Month must be 1..12" }
    return when (calendarSystem) {
      CalendarSystem.JALALI ->
        if (language == AppLanguage.PERSIAN) persianMonths[month - 1]
        else jalaliEnglishMonths[month - 1]
      CalendarSystem.GREGORIAN ->
        if (language == AppLanguage.PERSIAN) gregorianPersianMonths[month - 1]
        else gregorianEnglishMonths[month - 1]
    }
  }

  fun weekdayName(dayOfWeek: Int, language: AppLanguage): String {
    return if (language == AppLanguage.PERSIAN) {
      persianWeekdays[dayOfWeek].orEmpty()
    } else {
      englishWeekdays[dayOfWeek].orEmpty()
    }
  }

  fun formatFullDate(
    utcMillis: Long,
    zoneId: String,
    calendarSystem: CalendarSystem,
    language: AppLanguage
  ): String {
    val parts = utcMillisToZonedParts(utcMillis, zoneId, calendarSystem)
    val weekday = weekdayName(parts.dayOfWeek, language)
    val monthStr = monthName(parts.month, calendarSystem, language)
    return if (language == AppLanguage.PERSIAN) {
      toPersianDigits("$weekday، ${parts.day} $monthStr ${parts.year}")
    } else {
      "$weekday, $monthStr ${parts.day}, ${parts.year}"
    }
  }

  fun formatMonthYear(
    year: Int,
    month: Int,
    calendarSystem: CalendarSystem,
    language: AppLanguage
  ): String {
    val monthStr = monthName(month, calendarSystem, language)
    return if (language == AppLanguage.PERSIAN) {
      toPersianDigits("$monthStr $year")
    } else {
      "$monthStr $year"
    }
  }

  fun formatShortDate(
    utcMillis: Long,
    zoneId: String,
    calendarSystem: CalendarSystem,
    language: AppLanguage
  ): String {
    val parts = utcMillisToZonedParts(utcMillis, zoneId, calendarSystem)
    val raw = "%04d/%02d/%02d".format(parts.year, parts.month, parts.day)
    return if (language == AppLanguage.PERSIAN) toPersianDigits(raw) else raw
  }

  fun formatTime(
    utcMillis: Long,
    zoneId: String,
    language: AppLanguage
  ): String {
    val tz = TimeZone.getTimeZone(zoneId)
    val cal = GregorianCalendar(tz).apply { timeInMillis = utcMillis }
    val raw = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    return if (language == AppLanguage.PERSIAN) toPersianDigits(raw) else raw
  }

  fun formatDateTime(
    utcMillis: Long,
    zoneId: String,
    calendarSystem: CalendarSystem,
    language: AppLanguage
  ): String {
    val dateStr = formatShortDate(utcMillis, zoneId, calendarSystem, language)
    val timeStr = formatTime(utcMillis, zoneId, language)
    return "$dateStr · $timeStr"
  }

  fun toPersianDigits(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input) {
      when (ch) {
        in '0'..'9' -> sb.append(('۰'.code + (ch - '0')).toChar())
        else -> sb.append(ch)
      }
    }
    return sb.toString()
  }

  fun formatNumber(value: Int, language: AppLanguage): String {
    val raw = value.toString()
    return if (language == AppLanguage.PERSIAN) toPersianDigits(raw) else raw
  }

  private fun floorDiv(a: Long, b: Long): Long {
    var q = a / b
    if ((a xor b) < 0L && q * b != a) q -= 1L
    return q
  }
}
