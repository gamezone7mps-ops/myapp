package com.example.domain.model

enum class RecurrenceRule(val key: String) {
  NONE("NONE"),
  DAILY("DAILY"),
  WEEKLY("WEEKLY"),
  MONTHLY("MONTHLY");

  companion object {
    fun fromKey(key: String?): RecurrenceRule =
      entries.firstOrNull { it.key == key } ?: NONE
  }
}
