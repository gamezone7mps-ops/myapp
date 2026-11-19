package com.example.core.calendar

import com.example.domain.model.AppLanguage
import com.example.domain.model.CalendarSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianCalendarEngineTest {

  @Test
  fun `jalali leap years follow 33-year astronomical cycle`() {
    assertTrue(PersianCalendarEngine.isJalaliLeapYear(1395))
    assertTrue(PersianCalendarEngine.isJalaliLeapYear(1399))
    assertFalse(PersianCalendarEngine.isJalaliLeapYear(1400))
    assertTrue(PersianCalendarEngine.isJalaliLeapYear(1403))
    assertFalse(PersianCalendarEngine.isJalaliLeapYear(1404))
    assertFalse(PersianCalendarEngine.isJalaliLeapYear(1405))
    assertTrue(PersianCalendarEngine.isJalaliLeapYear(1408))

    assertEquals(30, PersianCalendarEngine.jalaliMonthLength(1403, 12))
    assertEquals(29, PersianCalendarEngine.jalaliMonthLength(1404, 12))
  }

  @Test
  fun `gregorian leap years handle century rules accurately`() {
    assertTrue(PersianCalendarEngine.isGregorianLeapYear(2000))
    assertFalse(PersianCalendarEngine.isGregorianLeapYear(1900))
    assertTrue(PersianCalendarEngine.isGregorianLeapYear(2024))
    assertFalse(PersianCalendarEngine.isGregorianLeapYear(2025))
    assertEquals(29, PersianCalendarEngine.gregorianMonthLength(2024, 2))
    assertEquals(28, PersianCalendarEngine.gregorianMonthLength(2025, 2))
  }

  @Test
  fun `known nowruz and boundary dates convert accurately`() {
    assertEquals(
      CalendarDate(1348, 10, 11),
      PersianCalendarEngine.gregorianToJalali(1970, 1, 1)
    )
    assertEquals(
      CalendarDate(1399, 12, 30),
      PersianCalendarEngine.gregorianToJalali(2021, 3, 20)
    )
    assertEquals(
      CalendarDate(1400, 1, 1),
      PersianCalendarEngine.gregorianToJalali(2021, 3, 21)
    )
    assertEquals(
      CalendarDate(1403, 1, 1),
      PersianCalendarEngine.gregorianToJalali(2024, 3, 20)
    )
    assertEquals(
      CalendarDate(1403, 12, 30),
      PersianCalendarEngine.gregorianToJalali(2025, 3, 20)
    )
    assertEquals(
      CalendarDate(1404, 1, 1),
      PersianCalendarEngine.gregorianToJalali(2025, 3, 21)
    )
    assertEquals(
      CalendarDate(1405, 8, 28),
      PersianCalendarEngine.gregorianToJalali(2026, 11, 19)
    )
    assertEquals(
      CalendarDate(2026, 11, 19),
      PersianCalendarEngine.jalaliToGregorian(1405, 8, 28)
    )
  }

  @Test
  fun `round trip conversion holds across 20000 days`() {
    for (epochDay in -5000L..25000L) {
      val g = PersianCalendarEngine.epochDayToGregorian(epochDay)
      val j = PersianCalendarEngine.gregorianToJalali(g.year, g.month, g.day)
      val gBack = PersianCalendarEngine.jalaliToGregorian(j.year, j.month, j.day)
      assertEquals("Failed round trip at epochDay=$epochDay", g, gBack)
      assertEquals(epochDay, PersianCalendarEngine.jalaliToEpochDay(j.year, j.month, j.day))
      assertEquals(epochDay, PersianCalendarEngine.gregorianToEpochDay(g.year, g.month, g.day))
    }
  }

  @Test
  fun `utc millis conversion preserves timezone and dst without mutating underlying instant`() {
    val utcInstant = PersianCalendarEngine.zonedPartsToUtcMillis(
      year = 1405,
      month = 8,
      day = 28,
      hour = 14,
      minute = 30,
      zoneId = "Asia/Tehran",
      calendarSystem = CalendarSystem.JALALI
    )

    val jalaliTehran = PersianCalendarEngine.utcMillisToZonedParts(
      utcMillis = utcInstant,
      zoneId = "Asia/Tehran",
      calendarSystem = CalendarSystem.JALALI
    )
    assertEquals(1405, jalaliTehran.year)
    assertEquals(8, jalaliTehran.month)
    assertEquals(28, jalaliTehran.day)
    assertEquals(14, jalaliTehran.hour)
    assertEquals(30, jalaliTehran.minute)

    val gregorianTehran = PersianCalendarEngine.utcMillisToZonedParts(
      utcMillis = utcInstant,
      zoneId = "Asia/Tehran",
      calendarSystem = CalendarSystem.GREGORIAN
    )
    assertEquals(2026, gregorianTehran.year)
    assertEquals(11, gregorianTehran.month)
    assertEquals(19, gregorianTehran.day)

    val shortFa = PersianCalendarEngine.formatShortDate(
      utcMillis = utcInstant,
      zoneId = "Asia/Tehran",
      calendarSystem = CalendarSystem.JALALI,
      language = AppLanguage.PERSIAN
    )
    assertEquals("۱۴۰۵/۰۸/۲۸", shortFa)
  }
}
