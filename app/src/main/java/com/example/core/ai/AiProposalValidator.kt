package com.example.core.ai

import com.example.core.calendar.CalendarDate
import com.example.core.calendar.PersianCalendarEngine
import com.example.core.common.InputValidators
import com.example.domain.model.AppLanguage
import com.example.domain.model.CalendarSystem
import com.example.domain.model.MemoryEntryType
import com.example.domain.model.RecurrenceRule
import com.example.domain.model.TaskPriority
import com.example.domain.model.TaskStatus
import com.example.domain.model.UserPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Validates and converts structured JSON responses produced by Gemini via responseSchema
 * into safe domain proposals or clarification prompts. Never permits malformed or unverified writes.
 */
object AiProposalValidator {

  private val validModes = setOf(
    "REPLY",
    "CLARIFY",
    "PROPOSE_CREATE_TASK",
    "PROPOSE_UPDATE_TASK",
    "PROPOSE_CREATE_EVENT",
    "PROPOSE_UPDATE_EVENT",
    "PROPOSE_CREATE_MEMORY"
  )

  fun parseAndValidate(
    rawJson: String,
    snapshot: WorkspaceContextSnapshot,
    preferences: UserPreferences
  ): AiTurnOutcome {
    val json = try {
      JSONObject(rawJson)
    } catch (e: Exception) {
      return AiTurnOutcome.Unavailable(
        reason = AiUnavailableReason.SERVICE_ERROR,
        detail = e.localizedMessage ?: "Invalid structured response from AI."
      )
    }

    val mode = json.optString("mode", "").trim().uppercase()
    if (mode !in validModes) {
      return AiTurnOutcome.Unavailable(
        reason = AiUnavailableReason.SERVICE_ERROR,
        detail = "Unsupported AI response mode: $mode"
      )
    }

    val replyText = json.optString("replyText", "").trim()
    val suggestions = extractStringList(json.optJSONArray("clarificationSuggestions")).take(4)
    val referencedItems = extractStringList(json.optJSONArray("referencedTitles")).take(5)

    return when (mode) {
      "REPLY" -> {
        if (replyText.isEmpty()) {
          clarifyFallback(
            preferences.language,
            en = "I could not compose a clear reply. Could you rephrase your request?",
            fa = "پاسخ روشنی دریافت نشد. لطفاً درخواست خود را دقیق‌تر بنویسید.",
            suggestions = suggestions
          )
        } else {
          AiTurnOutcome.Reply(replyText = replyText, referencedItems = referencedItems)
        }
      }

      "CLARIFY" -> {
        val question = replyText.ifEmpty {
          localizedText(
            preferences.language,
            en = "Could you clarify the missing details before I prepare an action?",
            fa = "لطفاً جزئیات ناقص را مشخص کنید تا پیش‌نمایش دقیق آماده شود."
          )
        }
        AiTurnOutcome.ClarificationNeeded(questionText = question, suggestions = suggestions)
      }

      "PROPOSE_CREATE_TASK" -> validateCreateTask(json, replyText, referencedItems, preferences, suggestions)
      "PROPOSE_UPDATE_TASK" -> validateUpdateTask(json, replyText, referencedItems, snapshot, preferences, suggestions)
      "PROPOSE_CREATE_EVENT" -> validateCreateEvent(json, replyText, referencedItems, preferences, suggestions)
      "PROPOSE_UPDATE_EVENT" -> validateUpdateEvent(json, replyText, referencedItems, snapshot, preferences, suggestions)
      "PROPOSE_CREATE_MEMORY" -> validateCreateMemory(json, replyText, referencedItems, preferences, suggestions)
      else -> AiTurnOutcome.Unavailable(AiUnavailableReason.SERVICE_ERROR, "Unknown mode")
    }
  }

  private fun validateCreateTask(
    json: JSONObject,
    replyText: String,
    referencedItems: List<String>,
    preferences: UserPreferences,
    suggestions: List<String>
  ): AiTurnOutcome {
    val title = json.optString("title", "").trim()
    if (InputValidators.validateTaskTitle(title) != null) {
      return clarifyFallback(
        preferences.language,
        en = "What title would you like me to use for this task?",
        fa = "چه عنوانی برای این کار ثبت شود؟",
        suggestions = suggestions
      )
    }

    val dateStr = json.optString("dateIso", "").trim()
    val timeStr = json.optString("dueTime", "").trim().ifEmpty { json.optString("startTime", "").trim() }
    val dueAtUtcMillis = if (dateStr.isNotEmpty()) {
      val date = parseIsoGregorianDate(dateStr) ?: return clarifyFallback(
        preferences.language,
        en = "I could not verify the due date \"$dateStr\". Please specify the exact date.",
        fa = "تاریخ سررسید «$dateStr» معتبر نبود. لطفاً تاریخ دقیق را مشخص کنید.",
        suggestions = suggestions
      )
      val time = if (timeStr.isNotEmpty()) {
        parseTimeHm(timeStr) ?: return clarifyFallback(
          preferences.language,
          en = "I could not verify the due time \"$timeStr\". Please specify a valid HH:MM time.",
          fa = "ساعت «$timeStr» معتبر نبود. لطفاً ساعت دقیق را مشخص کنید.",
          suggestions = suggestions
        )
      } else {
        18 to 0
      }
      PersianCalendarEngine.zonedPartsToUtcMillis(
        year = date.year,
        month = date.month,
        day = date.day,
        hour = time.first,
        minute = time.second,
        zoneId = preferences.defaultZoneId,
        calendarSystem = CalendarSystem.GREGORIAN
      )
    } else {
      null
    }

    val priority = parsePriority(json.optString("priority", "")) ?: TaskPriority.MEDIUM
    val recurrence = parseRecurrence(json.optString("recurrence", ""))
    val reminder = sanitizeReminderMinutes(json.optIntOrNull("reminderMinutesBefore"))
    val description = json.optString("description", "").trim()
    val category = json.optString("category", "").trim().ifEmpty { null }

    val explanation = replyText.ifEmpty {
      localizedText(
        preferences.language,
        en = "Please review this task before I save it:",
        fa = "لطفاً پیش‌نمایش این کار را پیش از ذخیره بررسی و تایید کنید:"
      )
    }

    return AiTurnOutcome.Proposal(
      explanationText = explanation,
      action = AiProposedAction.CreateTaskProposal(
        title = title,
        description = description,
        dueAtUtcMillis = dueAtUtcMillis,
        priority = priority,
        category = category,
        recurrenceRule = recurrence,
        reminderMinutesBefore = if (dueAtUtcMillis != null) reminder else null
      ),
      referencedItems = referencedItems
    )
  }

  private fun validateUpdateTask(
    json: JSONObject,
    replyText: String,
    referencedItems: List<String>,
    snapshot: WorkspaceContextSnapshot,
    preferences: UserPreferences,
    suggestions: List<String>
  ): AiTurnOutcome {
    val targetId = json.optString("targetId", "").trim()
    val existing = snapshot.allTasks.firstOrNull { it.id == targetId && !it.isDeleted }
      ?: return clarifyFallback(
        preferences.language,
        en = "Which existing task did you want to update? Please name it so I don't modify the wrong item.",
        fa = "کدام کار موجود باید ویرایش شود؟ لطفاً عنوان دقیق‌تر آن را بنویسید.",
        suggestions = suggestions.ifEmpty {
          snapshot.matchedTasks.take(3).map { it.title }
        }
      )

    val newTitle = json.optString("title", "").trim().ifEmpty { existing.title }
    if (InputValidators.validateTaskTitle(newTitle) != null) {
      return clarifyFallback(
        preferences.language,
        en = "The updated task title cannot be empty.",
        fa = "عنوان جدید کار نمی‌تواند خالی باشد.",
        suggestions = suggestions
      )
    }

    val newDesc = if (json.has("description") && !json.isNull("description")) {
      json.optString("description", existing.description).trim()
    } else {
      existing.description
    }

    val dateStr = json.optString("dateIso", "").trim()
    val timeStr = json.optString("dueTime", "").trim().ifEmpty { json.optString("startTime", "").trim() }
    val updatedDueAt = if (dateStr.isNotEmpty()) {
      val date = parseIsoGregorianDate(dateStr) ?: return clarifyFallback(
        preferences.language,
        en = "I could not verify the new due date \"$dateStr\".",
        fa = "تاریخ جدید «$dateStr» معتبر نبود. لطفاً تاریخ دقیق را مشخص کنید.",
        suggestions = suggestions
      )
      val fallbackTime = existing.dueAtUtcMillis?.let {
        val parts = PersianCalendarEngine.utcMillisToZonedParts(it, preferences.defaultZoneId, CalendarSystem.GREGORIAN)
        parts.hour to parts.minute
      } ?: (18 to 0)
      val time = if (timeStr.isNotEmpty()) {
        parseTimeHm(timeStr) ?: fallbackTime
      } else {
        fallbackTime
      }
      PersianCalendarEngine.zonedPartsToUtcMillis(
        year = date.year,
        month = date.month,
        day = date.day,
        hour = time.first,
        minute = time.second,
        zoneId = preferences.defaultZoneId,
        calendarSystem = CalendarSystem.GREGORIAN
      )
    } else {
      existing.dueAtUtcMillis
    }

    val newPriority = parsePriority(json.optString("priority", "")) ?: existing.priority
    val newStatus = parseTaskStatus(json.optString("status", "")) ?: existing.status

    val explanation = replyText.ifEmpty {
      localizedText(
        preferences.language,
        en = "Review the task changes below before saving:",
        fa = "تغییرات زیر برای کار انتخاب‌شده را پیش از ذخیره بررسی کنید:"
      )
    }

    return AiTurnOutcome.Proposal(
      explanationText = explanation,
      action = AiProposedAction.UpdateTaskProposal(
        targetTaskId = existing.id,
        targetCurrentTitle = existing.title,
        newTitle = newTitle,
        newDescription = newDesc,
        newDueAtUtcMillis = updatedDueAt,
        newPriority = newPriority,
        newStatus = newStatus
      ),
      referencedItems = referencedItems
    )
  }

  private fun validateCreateEvent(
    json: JSONObject,
    replyText: String,
    referencedItems: List<String>,
    preferences: UserPreferences,
    suggestions: List<String>
  ): AiTurnOutcome {
    val title = json.optString("title", "").trim()
    val dateStr = json.optString("dateIso", "").trim()
    val startStr = json.optString("startTime", "").trim()
    val endStr = json.optString("endTime", "").trim()
    val isAllDay = json.optBoolean("allDay", false)

    if (title.isEmpty()) {
      return clarifyFallback(
        preferences.language,
        en = "What should the title of this calendar event be?",
        fa = "عنوان این رویداد چه باشد؟",
        suggestions = suggestions
      )
    }

    val date = parseIsoGregorianDate(dateStr) ?: return clarifyFallback(
      preferences.language,
      en = "Please specify the date for \"$title\".",
      fa = "لطفاً تاریخ دقیق رویداد «$title» را مشخص کنید.",
      suggestions = suggestions
    )

    val startUtc: Long
    val endUtc: Long
    if (isAllDay) {
      startUtc = PersianCalendarEngine.startOfDayUtcMillis(
        date,
        preferences.defaultZoneId,
        CalendarSystem.GREGORIAN
      )
      endUtc = PersianCalendarEngine.endOfDayUtcMillis(
        date,
        preferences.defaultZoneId,
        CalendarSystem.GREGORIAN
      )
    } else {
      val startHm = parseTimeHm(startStr) ?: return clarifyFallback(
        preferences.language,
        en = "What time does \"$title\" start?",
        fa = "رویداد «$title» از چه ساعتی شروع می‌شود؟",
        suggestions = suggestions
      )
      startUtc = PersianCalendarEngine.zonedPartsToUtcMillis(
        year = date.year,
        month = date.month,
        day = date.day,
        hour = startHm.first,
        minute = startHm.second,
        zoneId = preferences.defaultZoneId,
        calendarSystem = CalendarSystem.GREGORIAN
      )
      endUtc = if (endStr.isNotEmpty()) {
        val endHm = parseTimeHm(endStr) ?: return clarifyFallback(
          preferences.language,
          en = "The end time \"$endStr\" was not recognized. Please clarify the end time.",
          fa = "ساعت پایان «$endStr» معتبر نبود. لطفاً ساعت پایان را مشخص کنید.",
          suggestions = suggestions
        )
        PersianCalendarEngine.zonedPartsToUtcMillis(
          year = date.year,
          month = date.month,
          day = date.day,
          hour = endHm.first,
          minute = endHm.second,
          zoneId = preferences.defaultZoneId,
          calendarSystem = CalendarSystem.GREGORIAN
        )
      } else {
        startUtc + 3_600_000L
      }
    }

    if (InputValidators.validateEvent(title, startUtc, endUtc) != null) {
      return clarifyFallback(
        preferences.language,
        en = "The event end time appears to be earlier than its start time. Could you confirm the times?",
        fa = "ساعت پایان رویداد نباید قبل از ساعت شروع باشد. لطفاً زمان را اصلاح کنید.",
        suggestions = suggestions
      )
    }

    val explanation = replyText.ifEmpty {
      localizedText(
        preferences.language,
        en = "I found these event details. Confirm to save to your calendar:",
        fa = "جزئیات این رویداد آماده شد. برای ثبت در تقویم تایید کنید:"
      )
    }

    return AiTurnOutcome.Proposal(
      explanationText = explanation,
      action = AiProposedAction.CreateEventProposal(
        title = title,
        description = json.optString("description", "").trim(),
        location = json.optString("location", "").trim().ifEmpty { null },
        startAtUtcMillis = startUtc,
        endAtUtcMillis = endUtc,
        zoneId = preferences.defaultZoneId,
        isAllDay = isAllDay,
        reminderMinutesBefore = sanitizeReminderMinutes(json.optIntOrNull("reminderMinutesBefore")),
        recurrenceRule = parseRecurrence(json.optString("recurrence", ""))
      ),
      referencedItems = referencedItems
    )
  }

  private fun validateUpdateEvent(
    json: JSONObject,
    replyText: String,
    referencedItems: List<String>,
    snapshot: WorkspaceContextSnapshot,
    preferences: UserPreferences,
    suggestions: List<String>
  ): AiTurnOutcome {
    val targetId = json.optString("targetId", "").trim()
    val existing = snapshot.allEvents.firstOrNull { it.id == targetId && !it.isDeleted }
      ?: return clarifyFallback(
        preferences.language,
        en = "Which scheduled event should I update? Please specify the event name or date.",
        fa = "کدام رویداد تقویم باید ویرایش شود؟ لطفاً نام یا تاریخ آن را مشخص کنید.",
        suggestions = suggestions.ifEmpty {
          snapshot.matchedEvents.take(3).map { it.title }
        }
      )

    val newTitle = json.optString("title", "").trim().ifEmpty { existing.title }
    val newDesc = if (json.has("description") && !json.isNull("description")) {
      json.optString("description", existing.description).trim()
    } else {
      existing.description
    }
    val newLocation = if (json.has("location") && !json.isNull("location")) {
      json.optString("location", "").trim().ifEmpty { null }
    } else {
      existing.location
    }

    val oldDurationMs = (existing.endAtUtcMillis - existing.startAtUtcMillis).coerceAtLeast(1_800_000L)
    val existingStartParts = PersianCalendarEngine.utcMillisToZonedParts(
      existing.startAtUtcMillis,
      existing.zoneId,
      CalendarSystem.GREGORIAN
    )

    val dateStr = json.optString("dateIso", "").trim()
    val startStr = json.optString("startTime", "").trim()
    val endStr = json.optString("endTime", "").trim()

    val newDate = if (dateStr.isNotEmpty()) {
      parseIsoGregorianDate(dateStr) ?: return clarifyFallback(
        preferences.language,
        en = "I could not verify the updated date \"$dateStr\".",
        fa = "تاریخ جدید «$dateStr» معتبر نبود. لطفاً تاریخ دقیق را مشخص کنید.",
        suggestions = suggestions
      )
    } else {
      CalendarDate(existingStartParts.year, existingStartParts.month, existingStartParts.day)
    }

    val newStartHm = if (startStr.isNotEmpty()) {
      parseTimeHm(startStr) ?: return clarifyFallback(
        preferences.language,
        en = "I could not verify the updated start time \"$startStr\".",
        fa = "ساعت شروع جدید «$startStr» معتبر نبود.",
        suggestions = suggestions
      )
    } else {
      existingStartParts.hour to existingStartParts.minute
    }

    val newStartUtc = PersianCalendarEngine.zonedPartsToUtcMillis(
      year = newDate.year,
      month = newDate.month,
      day = newDate.day,
      hour = newStartHm.first,
      minute = newStartHm.second,
      zoneId = existing.zoneId,
      calendarSystem = CalendarSystem.GREGORIAN
    )

    val newEndUtc = if (endStr.isNotEmpty()) {
      val endHm = parseTimeHm(endStr) ?: return clarifyFallback(
        preferences.language,
        en = "I could not verify the updated end time \"$endStr\".",
        fa = "ساعت پایان جدید «$endStr» معتبر نبود.",
        suggestions = suggestions
      )
      PersianCalendarEngine.zonedPartsToUtcMillis(
        year = newDate.year,
        month = newDate.month,
        day = newDate.day,
        hour = endHm.first,
        minute = endHm.second,
        zoneId = existing.zoneId,
        calendarSystem = CalendarSystem.GREGORIAN
      )
    } else {
      newStartUtc + oldDurationMs
    }

    if (InputValidators.validateEvent(newTitle, newStartUtc, newEndUtc) != null) {
      return clarifyFallback(
        preferences.language,
        en = "Updated event times are invalid (end time is before start time).",
        fa = "زمان‌بندی جدید رویداد معتبر نیست (زمان پایان قبل از شروع است).",
        suggestions = suggestions
      )
    }

    val explanation = replyText.ifEmpty {
      localizedText(
        preferences.language,
        en = "Review the updated event details before saving:",
        fa = "جزئیات ویرایش‌شده رویداد را پیش از ذخیره تایید کنید:"
      )
    }

    return AiTurnOutcome.Proposal(
      explanationText = explanation,
      action = AiProposedAction.UpdateEventProposal(
        targetEventId = existing.id,
        targetCurrentTitle = existing.title,
        newTitle = newTitle,
        newDescription = newDesc,
        newLocation = newLocation,
        newStartAtUtcMillis = newStartUtc,
        newEndAtUtcMillis = newEndUtc,
        zoneId = existing.zoneId
      ),
      referencedItems = referencedItems
    )
  }

  private fun validateCreateMemory(
    json: JSONObject,
    replyText: String,
    referencedItems: List<String>,
    preferences: UserPreferences,
    suggestions: List<String>
  ): AiTurnOutcome {
    val title = json.optString("title", "").trim()
    val content = json.optString("description", "").trim()
    val rawUrl = json.optString("sourceUrl", "").trim().ifEmpty { null }
    val validUrl = rawUrl?.takeIf { InputValidators.isValidHttpUrl(it) }

    if (InputValidators.validateMemory(title, content, validUrl) != null) {
      return clarifyFallback(
        preferences.language,
        en = "What note, fact, or idea should I save to Smart Memory?",
        fa = "چه نکته، یادداشت یا ایده‌ای در حافظه هوشمند ذخیره شود؟",
        suggestions = suggestions
      )
    }

    val entryType = parseMemoryType(json.optString("entryType", "")) ?: MemoryEntryType.NOTE
    val tags = extractStringList(json.optJSONArray("tags"))
      .map { it.trim().trimStart('#') }
      .filter { it.isNotEmpty() }
      .distinct()
      .take(6)

    val explanation = replyText.ifEmpty {
      localizedText(
        preferences.language,
        en = "Review this Smart Memory entry before I save it:",
        fa = "این مورد برای ثبت در حافظه هوشمند آماده است؛ لطفاً تایید کنید:"
      )
    }

    return AiTurnOutcome.Proposal(
      explanationText = explanation,
      action = AiProposedAction.CreateMemoryProposal(
        title = title.ifEmpty { content.take(48) },
        content = content,
        entryType = entryType,
        tags = tags,
        sourceUrl = validUrl
      ),
      referencedItems = referencedItems
    )
  }

  fun parseIsoGregorianDate(raw: String): CalendarDate? {
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(raw.trim()) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    if (year !in 1970..2100 || month !in 1..12) return null
    val maxDay = PersianCalendarEngine.gregorianMonthLength(year, month)
    if (day !in 1..maxDay) return null
    return CalendarDate(year, month, day)
  }

  fun parseTimeHm(raw: String): Pair<Int, Int>? {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(raw.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour to minute
  }

  private fun parsePriority(raw: String): TaskPriority? = when (raw.trim().uppercase()) {
    "LOW" -> TaskPriority.LOW
    "MEDIUM" -> TaskPriority.MEDIUM
    "HIGH" -> TaskPriority.HIGH
    "URGENT" -> TaskPriority.URGENT
    else -> null
  }

  private fun parseTaskStatus(raw: String): TaskStatus? = when (raw.trim().uppercase()) {
    "TODO" -> TaskStatus.TODO
    "IN_PROGRESS" -> TaskStatus.IN_PROGRESS
    "COMPLETED" -> TaskStatus.COMPLETED
    "ARCHIVED" -> TaskStatus.ARCHIVED
    else -> null
  }

  private fun parseRecurrence(raw: String): String? = when (raw.trim().uppercase()) {
    RecurrenceRule.DAILY.key -> RecurrenceRule.DAILY.key
    RecurrenceRule.WEEKLY.key -> RecurrenceRule.WEEKLY.key
    RecurrenceRule.MONTHLY.key -> RecurrenceRule.MONTHLY.key
    else -> null
  }

  private fun parseMemoryType(raw: String): MemoryEntryType? = when (raw.trim().uppercase()) {
    "NOTE" -> MemoryEntryType.NOTE
    "IDEA" -> MemoryEntryType.IDEA
    "LINK" -> MemoryEntryType.LINK
    "FACT" -> MemoryEntryType.FACT
    "REFERENCE" -> MemoryEntryType.REFERENCE
    else -> null
  }

  private fun sanitizeReminderMinutes(raw: Int?): Int? {
    if (raw == null || raw < 0) return null
    return when {
      raw == 0 -> 0
      raw <= 15 -> 10
      raw <= 45 -> 30
      raw <= 120 -> 60
      else -> 1440
    }
  }

  private fun extractStringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList {
      for (i in 0 until array.length()) {
        val value = array.optString(i, "").trim()
        if (value.isNotEmpty()) add(value)
      }
    }
  }

  private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key, -1).takeIf { it >= 0 }
  }

  private fun clarifyFallback(
    language: AppLanguage,
    en: String,
    fa: String,
    suggestions: List<String>
  ): AiTurnOutcome.ClarificationNeeded = AiTurnOutcome.ClarificationNeeded(
    questionText = localizedText(language, en, fa),
    suggestions = suggestions
  )

  private fun localizedText(language: AppLanguage, en: String, fa: String): String =
    if (language == AppLanguage.PERSIAN) fa else en
}
