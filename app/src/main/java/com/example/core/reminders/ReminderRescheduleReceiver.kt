package com.example.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.common.DefaultLifeOsContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderRescheduleReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (action != Intent.ACTION_BOOT_COMPLETED &&
      action != Intent.ACTION_TIMEZONE_CHANGED &&
      action != Intent.ACTION_TIME_CHANGED
    ) {
      return
    }

    val pendingResult = goAsync()
    val appContext = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
      try {
        DefaultLifeOsContainer(appContext).reminderScheduler.rescheduleAllActiveReminders()
      } finally {
        pendingResult.finish()
      }
    }
  }
}
