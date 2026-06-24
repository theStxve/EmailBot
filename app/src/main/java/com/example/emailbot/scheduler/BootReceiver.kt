package com.example.emailbot.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.emailbot.data.EmailTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores all active scheduled alarms after a device reboot.
 * Without this, AlarmManager alarms are lost on reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = EmailTaskRepository(context)
                repo.getActiveTasks().forEach { task ->
                    EmailScheduler.schedule(context, task)
                }
                // Also restart the foreground service after reboot
                EmailSchedulerService.start(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
