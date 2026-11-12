package com.example.emailbot.scheduler

import android.Manifest
import android.app.Service
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.emailbot.data.EmailTaskRepository
import com.example.emailbot.data.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Low-priority foreground service that runs while at least one schedule is active.
 *
 * The alarms work without it. Its only job is to keep the process "important" on
 * OEM ROMs (Samsung, Xiaomi, Huawei …) that otherwise kill background apps and
 * drop their alarms. It stops itself as soon as nothing is scheduled.
 */
class EmailSchedulerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val TAG = "EmailSchedulerService"

        /** Starts the service if any schedule is active. The service stops itself otherwise. */
        suspend fun sync(context: Context) {
            if (EmailTaskRepository(context).getActiveTasks().isEmpty()) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, EmailSchedulerService::class.java))
            } catch (e: Exception) {
                // Android 12+ forbids starting it from some background contexts. The alarms still work.
                Log.w(TAG, "Could not start service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        ServiceCompat.startForeground(
            this,
            Notifications.ID_SCHEDULER,
            Notifications.scheduler(this, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        scope.launch {
            EmailTaskRepository(applicationContext).tasks
                .map { tasks -> tasks.count { it.isActive } }
                .distinctUntilChanged()
                .collect { count ->
                    if (count == 0) {
                        stopSelf()
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(this@EmailSchedulerService, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        NotificationManagerCompat.from(this@EmailSchedulerService)
                            .notify(Notifications.ID_SCHEDULER, Notifications.scheduler(this@EmailSchedulerService, count))
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
