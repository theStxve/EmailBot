package com.example.emailbot.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.emailbot.data.EmailTaskRepository
import com.example.emailbot.data.isRecurring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired by AlarmManager. Registers the next alarm for recurring tasks right away
 * (so the chain never breaks, whatever happens to this send) and hands the send to WorkManager.
 */
class EmailAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(EmailScheduler.EXTRA_TASK_ID, -1)
        if (taskId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = EmailTaskRepository(context).getById(taskId) ?: return@launch
                if (!task.isEnabled) return@launch
                if (task.isRecurring) EmailScheduler.schedule(context, task)
                EmailScheduler.enqueueSend(context, task, manual = false)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
