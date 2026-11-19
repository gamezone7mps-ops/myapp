package com.example.core.common

import java.net.URI

enum class ValidationError {
  TASK_TITLE_REQUIRED,
  EVENT_TITLE_REQUIRED,
  EVENT_END_BEFORE_START,
  MEMORY_EMPTY,
  MEMORY_INVALID_URL,
  DOCUMENT_TITLE_REQUIRED
}

object InputValidators {

  fun validateTaskTitle(title: String): ValidationError? {
    return if (title.trim().isEmpty()) ValidationError.TASK_TITLE_REQUIRED else null
  }

  fun validateDocumentTitle(title: String): ValidationError? {
    return if (title.trim().isEmpty()) ValidationError.DOCUMENT_TITLE_REQUIRED else null
  }

  fun validateEvent(
    title: String,
    startAtUtcMillis: Long,
    endAtUtcMillis: Long
  ): ValidationError? {
    if (title.trim().isEmpty()) return ValidationError.EVENT_TITLE_REQUIRED
    if (endAtUtcMillis < startAtUtcMillis) return ValidationError.EVENT_END_BEFORE_START
    return null
  }

  fun validateMemory(
    title: String,
    content: String,
    sourceUrl: String?
  ): ValidationError? {
    if (title.trim().isEmpty() && content.trim().isEmpty()) {
      return ValidationError.MEMORY_EMPTY
    }
    val trimmedUrl = sourceUrl?.trim().orEmpty()
    if (trimmedUrl.isNotEmpty() && !isValidHttpUrl(trimmedUrl)) {
      return ValidationError.MEMORY_INVALID_URL
    }
    return null
  }

  fun isValidHttpUrl(url: String): Boolean {
    return try {
      val uri = URI(url.trim())
      val scheme = uri.scheme?.lowercase()
      (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) {
      false
    }
  }
}
