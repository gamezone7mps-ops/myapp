package com.example.features.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.calendar.CalendarDate
import com.example.core.calendar.PersianCalendarEngine
import com.example.domain.model.AppLanguage
import com.example.domain.model.CalendarSystem

@Composable
fun BilingualDatePickerDialog(
  initialDate: CalendarDate,
  calendarSystem: CalendarSystem,
  language: AppLanguage,
  zoneId: String,
  onDismiss: () -> Unit,
  onConfirm: (CalendarDate) -> Unit
) {
  var displayYear by remember(initialDate) { mutableIntStateOf(initialDate.year) }
  var displayMonth by remember(initialDate) { mutableIntStateOf(initialDate.month) }
  var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }

  val calendarLabel = stringResource(
    if (calendarSystem == CalendarSystem.JALALI) R.string.calendar_jalali
    else R.string.calendar_gregorian
  )
  val headers = remember(calendarSystem, language) {
    PersianCalendarEngine.weekdayShortHeaders(calendarSystem, language)
  }
  val firstOffset = remember(displayYear, displayMonth, zoneId, calendarSystem) {
    PersianCalendarEngine.dayOfWeekGridIndex(
      CalendarDate(displayYear, displayMonth, 1),
      zoneId,
      calendarSystem
    )
  }
  val daysInMonth = remember(displayYear, displayMonth, calendarSystem) {
    PersianCalendarEngine.monthLength(displayYear, displayMonth, calendarSystem)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.picker_select_date, calendarLabel),
        style = MaterialTheme.typography.titleMedium
      )
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = {
              val (y, m) = PersianCalendarEngine.shiftMonth(displayYear, displayMonth, -1)
              displayYear = y
              displayMonth = m
            },
            modifier = Modifier.testTag("date_picker_prev_month")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
              contentDescription = stringResource(R.string.cd_previous_period)
            )
          }
          Text(
            text = PersianCalendarEngine.formatMonthYear(displayYear, displayMonth, calendarSystem, language),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
          )
          IconButton(
            onClick = {
              val (y, m) = PersianCalendarEngine.shiftMonth(displayYear, displayMonth, 1)
              displayYear = y
              displayMonth = m
            },
            modifier = Modifier.testTag("date_picker_next_month")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = stringResource(R.string.cd_next_period)
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
          headers.forEach { h ->
            Text(
              text = h,
              modifier = Modifier.weight(1f),
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val totalCells = firstOffset + daysInMonth
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
          Row(modifier = Modifier.fillMaxWidth()) {
            for (col in 0..6) {
              val cellIndex = row * 7 + col
              val dayNum = cellIndex - firstOffset + 1
              if (dayNum in 1..daysInMonth) {
                val cellDate = CalendarDate(displayYear, displayMonth, dayNum)
                val isSelected = cellDate == selectedDate
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(
                      if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surface
                    )
                    .clickable { selectedDate = cellDate },
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = PersianCalendarEngine.formatNumber(dayNum, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                  )
                }
              } else {
                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(selectedDate) },
        modifier = Modifier.testTag("date_picker_confirm")
      ) {
        Text(stringResource(R.string.action_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_cancel))
      }
    }
  )
}

@Composable
fun SimpleTimePickerDialog(
  initialHour: Int,
  initialMinute: Int,
  language: AppLanguage,
  onDismiss: () -> Unit,
  onConfirm: (hour: Int, minute: Int) -> Unit
) {
  var hour by remember(initialHour) { mutableIntStateOf(initialHour.coerceIn(0, 23)) }
  var minute by remember(initialMinute) { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.picker_select_time),
        style = MaterialTheme.typography.titleMedium
      )
    },
    text = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TimeSpinnerColumn(
          label = stringResource(R.string.picker_hour),
          valueText = PersianCalendarEngine.toPersianDigitsIfNeeded("%02d".format(hour), language),
          onIncrement = { hour = (hour + 1) % 24 },
          onDecrement = { hour = (hour + 23) % 24 }
        )
        Text(text = ":", style = MaterialTheme.typography.headlineMedium)
        TimeSpinnerColumn(
          label = stringResource(R.string.picker_minute),
          valueText = PersianCalendarEngine.toPersianDigitsIfNeeded("%02d".format(minute), language),
          onIncrement = { minute = (minute + 5) % 60 },
          onDecrement = { minute = (minute + 55) % 60 }
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(hour, minute) },
        modifier = Modifier.testTag("time_picker_confirm")
      ) {
        Text(stringResource(R.string.action_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_cancel))
      }
    }
  )
}

@Composable
private fun TimeSpinnerColumn(
  label: String,
  valueText: String,
  onIncrement: () -> Unit,
  onDecrement: () -> Unit
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(
      onClick = onIncrement,
      modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    ) {
      Text("+", style = MaterialTheme.typography.titleLarge)
    }
    Text(text = valueText, style = MaterialTheme.typography.headlineMedium)
    TextButton(
      onClick = onDecrement,
      modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    ) {
      Text("-", style = MaterialTheme.typography.titleLarge)
    }
  }
}

private fun PersianCalendarEngine.toPersianDigitsIfNeeded(raw: String, language: AppLanguage): String {
  return if (language == AppLanguage.PERSIAN) toPersianDigits(raw) else raw
}
