package com.example.emailbot.ui.main

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScheduleSend
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.emailbot.data.EmailLog
import com.example.emailbot.data.EmailStatus
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.ScheduleType
import com.example.emailbot.data.SmtpSettings
import com.example.emailbot.data.displayTitle
import com.example.emailbot.data.isActive
import com.example.emailbot.scheduler.EmailScheduler
import com.example.emailbot.theme.StatusFailed
import com.example.emailbot.theme.StatusPaused
import com.example.emailbot.theme.StatusPending
import com.example.emailbot.theme.StatusSuccess
import com.example.emailbot.ui.formatRelative
import com.example.emailbot.ui.scheduleSummary

// ─── Task list ───────────────────────────────────────────────────────────────

@Composable
fun TaskListTab(
    tasks: List<EmailTask>?,
    accounts: List<SmtpSettings>?,
    onOpenAccounts: () -> Unit,
    onNewTask: () -> Unit,
    onEdit: (EmailTask) -> Unit,
    onToggle: (EmailTask) -> Unit,
    onDelete: (EmailTask) -> Unit,
    onSendNow: (EmailTask) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks == null || accounts == null) return // still loading

    val active = tasks.filter { it.isEnabled }
    val paused = tasks.filter { !it.isEnabled && !(it.scheduleType == ScheduleType.ONCE && it.status == EmailStatus.SENT) }
    val done = tasks.filter { !it.isEnabled && it.scheduleType == ScheduleType.ONCE && it.status == EmailStatus.SENT }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "permissions") { PermissionsCard() }

        if (accounts.isEmpty()) {
            item(key = "setup") {
                OnboardingState(
                    icon = Icons.Outlined.AlternateEmail,
                    title = "Willkommen bei EmailBot",
                    text = "Richte zuerst das E-Mail-Konto ein, über das deine geplanten E-Mails verschickt werden.",
                    action = "E-Mail-Konto einrichten",
                    onAction = onOpenAccounts
                )
            }
        } else if (tasks.isEmpty()) {
            item(key = "empty") {
                OnboardingState(
                    icon = Icons.Outlined.ScheduleSend,
                    title = "Noch nichts geplant",
                    text = "Plane eine E-Mail einmalig, in festen Abständen oder jede Woche zur selben Zeit.",
                    action = "Erste E-Mail planen",
                    onAction = onNewTask
                )
            }
        }

        fun section(title: String, list: List<EmailTask>) {
            if (list.isEmpty()) return
            item(key = "header_$title") { SectionLabel(title, list.size) }
            items(list, key = { it.id }) { task ->
                TaskCard(task, onEdit, onToggle, onDelete, onSendNow, Modifier.animateItem())
            }
        }
        section("Aktiv", active)
        section("Pausiert", paused)
        section("Erledigt", done)
    }
}

@Composable
private fun SectionLabel(title: String, count: Int) {
    Text(
        "$title · $count",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
private fun OnboardingState(icon: ImageVector, title: String, text: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(action)
        }
    }
}

// ─── Permissions ─────────────────────────────────────────────────────────────

private data class PermissionState(val exactAlarms: Boolean, val battery: Boolean, val notifications: Boolean) {
    val allGranted get() = exactAlarms && battery && notifications
}

private fun readPermissions(context: Context) = PermissionState(
    exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
    battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
    notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
)

/** Shows what's missing for reliable background sends. Re-checks every time the app comes back to the foreground. */
@Composable
private fun PermissionsCard() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readPermissions(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = readPermissions(context) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state = readPermissions(context)
    }

    if (state.allGranted) return

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = StatusPending.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = StatusPending)
                Spacer(Modifier.width(10.dp))
                Text("Für pünktlichen Versand", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Android bremst Apps im Hintergrund aus. Mit diesen Freigaben gehen deine E-Mails trotzdem pünktlich raus:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.exactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionRow("Wecker & Erinnerungen", "Sendet auf die Minute genau") {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
            if (!state.battery) {
                PermissionRow("Akku-Optimierung aus", "Verhindert, dass Android den Versand verschiebt") {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
            if (!state.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow("Benachrichtigungen", "Meldet, wenn eine E-Mail nicht rausging") {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onGrant) { Text("Erlauben") }
    }
}

// ─── Task card ───────────────────────────────────────────────────────────────

@Composable
private fun TaskCard(
    task: EmailTask,
    onEdit: (EmailTask) -> Unit,
    onToggle: (EmailTask) -> Unit,
    onDelete: (EmailTask) -> Unit,
    onSendNow: (EmailTask) -> Unit,
    modifier: Modifier = Modifier
) {
    val finished = task.scheduleType == ScheduleType.ONCE && task.status == EmailStatus.SENT
    val failed = task.status == EmailStatus.FAILED
    val accent = when {
        failed -> StatusFailed
        finished -> StatusSuccess
        !task.isEnabled -> StatusPaused
        else -> MaterialTheme.colorScheme.primary
    }
    val typeIcon = when {
        finished -> Icons.Outlined.MarkEmailRead
        task.scheduleType == ScheduleType.ONCE -> Icons.Outlined.Schedule
        task.scheduleType == ScheduleType.PERIODIC -> Icons.Outlined.Repeat
        else -> Icons.Outlined.CalendarMonth
    }

    Card(
        onClick = { onEdit(task) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isEnabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isEnabled) 1.dp else 0.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(typeIcon, null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        task.recipientTo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!finished) {
                    Switch(checked = task.isEnabled, onCheckedChange = { onToggle(task) })
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(scheduleSummary(task), MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f, fill = false))
                if (task.requireWifi) ConditionIcon(Icons.Outlined.Wifi, "Nur im WLAN")
                if (task.requireCharging) ConditionIcon(Icons.Outlined.BatteryChargingFull, "Nur beim Laden")
                if (task.requireMinBattery > 0) ConditionIcon(Icons.Outlined.BatteryStd, "Ab ${task.requireMinBattery} % Akku")
                if (task.attachmentName.isNotBlank()) ConditionIcon(Icons.Outlined.AttachFile, "Anhang: ${task.attachmentName}")
            }

            Spacer(Modifier.height(8.dp))
            StatusLine(task, finished, failed)

            HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSendNow(task) }) {
                    Icon(if (failed) Icons.Outlined.Refresh else Icons.AutoMirrored.Outlined.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            failed -> "Erneut senden"
                            finished -> "Nochmal senden"
                            else -> "Jetzt senden"
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onEdit(task) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Bearbeiten", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDelete(task) }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(task: EmailTask, finished: Boolean, failed: Boolean) {
    when {
        failed -> StatusText(Icons.Outlined.ErrorOutline, task.lastErrorMessage.ifBlank { "Senden fehlgeschlagen" }, StatusFailed, maxLines = 3)
        finished -> StatusText(Icons.Default.CheckCircle, "Gesendet · ${formatRelative(task.lastSentTimeMs)}", StatusSuccess)
        task.isActive -> {
            StatusText(Icons.Outlined.ScheduleSend, "Nächster Versand: ${formatRelative(EmailScheduler.nextTriggerMs(task))}", MaterialTheme.colorScheme.primary)
            if (task.lastErrorMessage.isNotBlank()) {
                StatusText(Icons.Outlined.Info, task.lastErrorMessage, StatusPending, maxLines = 2)
            }
        }
        else -> StatusText(Icons.Outlined.Info, "Pausiert", StatusPaused)
    }
    if (task.scheduleType != ScheduleType.ONCE && task.lastSentTimeMs > 0) {
        Text(
            "Zuletzt gesendet: ${formatRelative(task.lastSentTimeMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, top = 2.dp)
        )
    }
}

@Composable
private fun StatusText(icon: ImageVector, text: String, color: Color, maxLines: Int = 1) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 1.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ConditionIcon(icon: ImageVector, description: String) {
    Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
}

// ─── History ─────────────────────────────────────────────────────────────────

@Composable
fun HistoryTab(
    logs: List<EmailLog>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (logs.isEmpty()) {
        Column(
            modifier = modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.Inbox, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            Text("Noch nichts gesendet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hier siehst du jeden Versuch, mit Uhrzeit und Fehlermeldung.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val sent = logs.count { it.status == EmailStatus.SENT }
    val failed = logs.size - sent

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "summary") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "$sent gesendet · $failed fehlgeschlagen",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                OutlinedButton(onClick = onClear) {
                    Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Leeren")
                }
            }
        }
        items(logs, key = { it.id }) { log ->
            val ok = log.status == EmailStatus.SENT
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        if (ok) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = if (ok) "Gesendet" else "Fehlgeschlagen",
                        tint = if (ok) StatusSuccess else StatusFailed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                log.taskLabel.ifBlank { log.subject.ifBlank { "(kein Betreff)" } },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatRelative(log.sentTimeMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            "An ${log.recipient}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!ok && log.errorMessage.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(log.errorMessage, style = MaterialTheme.typography.bodySmall, color = StatusFailed, maxLines = 4)
                        }
                    }
                }
            }
        }
    }
}
