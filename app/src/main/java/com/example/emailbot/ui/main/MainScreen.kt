package com.example.emailbot.ui.main

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emailbot.data.EmailLog
import com.example.emailbot.data.EmailStatus
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.ScheduleType
import com.example.emailbot.data.SmtpSettingsRepository
import com.example.emailbot.util.AttachmentHelper
import com.example.emailbot.util.ImportExportHelper
import java.text.SimpleDateFormat
import java.util.*

private val ColorSuccess = Color(0xFF22C55E)
private val ColorPending = Color(0xFFF59E0B)
private val ColorFailed  = Color(0xFFEF4444)
private val ColorPaused  = Color(0xFF94A3B8)

private val gradientHeader = Brush.linearGradient(
    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    vm: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val logs  by vm.logs.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<EmailTask?>(null) }

    val scheduledTasks = tasks.filter { it.status != EmailStatus.SENT || it.scheduleType != ScheduleType.ONCE }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val imported = ImportExportHelper.importTasks(context, uri)
            if (!imported.isNullOrEmpty()) {
                vm.importTasks(imported)
                Toast.makeText(context, "${imported.size} E-Mails importiert", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Import fehlgeschlagen (ungültiges JSON)", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gradientHeader)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "EmailBot",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${tasks.count { it.isEnabled && it.status == EmailStatus.PENDING }} aktiv · ${tasks.size} gesamt",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "SMTP Konten", tint = Color.White)
                    }

                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Optionen", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Backup importieren") },
                                onClick = {
                                    menuExpanded = false
                                    importLauncher.launch("*/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Backup exportieren") },
                                onClick = {
                                    menuExpanded = false
                                    val uri = ImportExportHelper.exportTasks(context, tasks)
                                    if (uri != null) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "E-Mail Backup teilen"))
                                    } else {
                                        Toast.makeText(context, "Export fehlgeschlagen", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { editingTask = null; showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Neue E-Mail") },
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    icon     = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    label    = { Text("Zeitplan") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    icon     = { Icon(Icons.Default.History, contentDescription = null) },
                    label    = { Text("Verlauf") }
                )
            }
        },
        modifier = modifier
    ) { padding ->
        when (selectedTab) {
            0 -> TaskListTab(
                tasks    = scheduledTasks,
                onToggle = { vm.toggleEnabled(it) },
                onEdit   = { editingTask = it; showAddDialog = true },
                onDelete = { vm.deleteTask(it) },
                onRetry  = { vm.retryTask(it) },
                onSendNow = { vm.sendNow(it) },
                modifier = Modifier.padding(padding)
            )
            1 -> HistoryTab(
                logs     = logs,
                onClear  = { vm.clearLogs() },
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showAddDialog) {
        TaskEditorDialog(
            initial  = editingTask,
            onSave   = { vm.addOrUpdateTask(it); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun TaskListTab(
    tasks: List<EmailTask>,
    onToggle: (EmailTask) -> Unit,
    onEdit: (EmailTask) -> Unit,
    onDelete: (EmailTask) -> Unit,
    onRetry: (EmailTask) -> Unit,
    onSendNow: (EmailTask) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasExactAlarmPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                am.canScheduleExactAlarms()
            } else true
        )
    }
    var hasBatteryExemption by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        if (!hasExactAlarmPermission || !hasBatteryExemption || !hasNotificationPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Systemberechtigungen fehlen", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Damit E-Mails exakt und zuverlässig im Hintergrund gesendet werden können, werden folgende Berechtigungen benötigt:", fontSize = 12.sp)

                    if (!hasExactAlarmPermission) {
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Exakte Alarme erlauben", fontSize = 11.sp)
                        }
                    }

                    if (!hasBatteryExemption) {
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Akku-Optimierung umgehen", fontSize = 11.sp)
                        }
                    }

                    if (!hasNotificationPermission) {
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    val activity = context as? android.app.Activity
                                    activity?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Benachrichtigungen erlauben", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MailOutline, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("Noch keine E-Mails geplant", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    Text("Tippe auf + um eine hinzuzufügen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(task, onToggle, onEdit, onDelete, onRetry, onSendNow)
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(
    task: EmailTask,
    onToggle: (EmailTask) -> Unit,
    onEdit: (EmailTask) -> Unit,
    onDelete: (EmailTask) -> Unit,
    onRetry: (EmailTask) -> Unit,
    onSendNow: (EmailTask) -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    val statusColor by animateColorAsState(
        targetValue = when (task.status) {
            EmailStatus.SENT    -> ColorSuccess
            EmailStatus.FAILED  -> ColorFailed
            EmailStatus.PAUSED  -> ColorPaused
            EmailStatus.PENDING -> if (task.isEnabled) ColorPending else ColorPaused
        },
        animationSpec = tween(300), label = "status"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isEnabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.label.ifBlank { task.subject.ifBlank { "(kein Betreff)" } },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "An: ${task.recipientTo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = task.isEnabled,
                    onCheckedChange = { onToggle(task) }
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = {},
                    label = {
                        val labelText = when (task.scheduleType) {
                            ScheduleType.ONCE -> sdf.format(Date(task.scheduledTimeMs))
                            ScheduleType.PERIODIC -> {
                                val intervalStr = if (task.intervalMinutes % 1.0 == 0.0) task.intervalMinutes.toLong().toString() else task.intervalMinutes.toString()
                                "Alle $intervalStr Min."
                            }
                            ScheduleType.CRON -> {
                                val dayStr = when (task.cronDayOfWeek) {
                                    -1 -> "Täglich"
                                    0 -> "Mo"
                                    1 -> "Di"
                                    2 -> "Mi"
                                    3 -> "Do"
                                    4 -> "Fr"
                                    5 -> "Sa"
                                    6 -> "So"
                                    else -> ""
                                }
                                val timeStr = String.format(Locale.getDefault(), "%02d:%02d", task.cronHour, task.cronMinute)
                                if (task.cronDayOfWeek == -1) "$dayStr um $timeStr" else "Jeden $dayStr um $timeStr"
                            }
                        }
                        Text(
                            text = labelText,
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        val icon = when (task.scheduleType) {
                            ScheduleType.ONCE -> Icons.Default.AccessTime
                            ScheduleType.PERIODIC -> Icons.Default.Repeat
                            ScheduleType.CRON -> Icons.Default.CalendarMonth
                        }
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
                StatusChip(task.status)
            }

            if (task.status == EmailStatus.FAILED && task.lastErrorMessage.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Fehler: ${task.lastErrorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorFailed,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (task.lastSentTimeMs > 0 && (task.scheduleType == ScheduleType.PERIODIC || task.scheduleType == ScheduleType.CRON)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Zuletzt gesendet: ${sdf.format(Date(task.lastSentTimeMs))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onSendNow(task) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF6366F1))
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Jetzt", fontSize = 12.sp)
                }
                TextButton(onClick = { onEdit(task) }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Bearbeiten", fontSize = 12.sp)
                }
                if (task.status == EmailStatus.FAILED) {
                    TextButton(onClick = { onRetry(task) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Erneut", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDelete(task) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Löschen", tint = ColorFailed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: EmailStatus) {
    val (label, color) = when (status) {
        EmailStatus.PENDING -> "Ausstehend" to ColorPending
        EmailStatus.SENT    -> "Gesendet"   to ColorSuccess
        EmailStatus.FAILED  -> "Fehlgeschlagen" to ColorFailed
        EmailStatus.PAUSED  -> "Pausiert"   to ColorPaused
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
    ) {
        Text(
            label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun HistoryTab(
    logs: List<EmailLog>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sdf = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Column(modifier = modifier.fillMaxSize()) {
        if (logs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClear) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Verlauf leeren", fontSize = 12.sp)
                }
            }
        }

        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("Kein Verlauf vorhanden", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (log.status == EmailStatus.SENT) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (log.status == EmailStatus.SENT) ColorSuccess else ColorFailed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = log.taskLabel.ifBlank { log.subject.ifBlank { "(kein Betreff)" } },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("An: ${log.recipient}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (log.sentTimeMs > 0)
                                    Text(sdf.format(Date(log.sentTimeMs)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                if (log.status == EmailStatus.FAILED && log.errorMessage.isNotBlank())
                                    Text("Fehler: ${log.errorMessage}", style = MaterialTheme.typography.bodySmall, color = ColorFailed, maxLines = 4)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorDialog(
    initial: EmailTask?,
    onSave: (EmailTask) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var label          by remember { mutableStateOf(initial?.label ?: "") }
    var recipientTo    by remember { mutableStateOf(initial?.recipientTo ?: "") }
    var recipientCc    by remember { mutableStateOf(initial?.recipientCc ?: "") }
    var recipientBcc   by remember { mutableStateOf(initial?.recipientBcc ?: "") }
    var subject        by remember { mutableStateOf(initial?.subject ?: "") }
    var body           by remember { mutableStateOf(initial?.body ?: "") }
    var scheduleType   by remember { mutableStateOf(initial?.scheduleType ?: ScheduleType.ONCE) }
    var intervalMinutes by remember {
        val defaultVal = initial?.intervalMinutes ?: 60.0
        val formatted = if (defaultVal % 1.0 == 0.0) defaultVal.toLong().toString() else defaultVal.toString()
        mutableStateOf(formatted)
    }

    var cronDayOfWeek  by remember { mutableIntStateOf(initial?.cronDayOfWeek ?: -1) }
    var cronHour       by remember { mutableIntStateOf(initial?.cronHour ?: 9) }
    var cronMinute     by remember { mutableIntStateOf(initial?.cronMinute ?: 0) }

    val smtpRepo = remember { SmtpSettingsRepository(context) }
    val accounts by smtpRepo.accounts.collectAsState(initial = emptyList())
    var selectedAccountId by remember { mutableIntStateOf(initial?.smtpAccountId ?: 0) }

    var attachmentPath by remember { mutableStateOf(initial?.attachmentPath ?: "") }
    var attachmentName by remember { mutableStateOf(initial?.attachmentName ?: "") }
    val createdAttachmentPaths = remember { mutableStateListOf<String>() }

    var requireWifi    by remember { mutableStateOf(initial?.requireWifi ?: false) }
    var requireCharging by remember { mutableStateOf(initial?.requireCharging ?: false) }
    var requireMinBattery by remember { mutableIntStateOf(initial?.requireMinBattery ?: 0) }

    val cal = remember {
        Calendar.getInstance().apply {
            if (initial != null && initial.scheduledTimeMs > 0)
                timeInMillis = initial.scheduledTimeMs
            else
                add(Calendar.HOUR_OF_DAY, 1)
        }
    }
    var scheduledMs by remember { mutableStateOf(cal.timeInMillis) }
    val sdf = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    var showCcBcc by remember { mutableStateOf(
        (initial?.recipientCc ?: "").isNotBlank() || (initial?.recipientBcc ?: "").isNotBlank()
    )}
    var toError by remember { mutableStateOf(false) }
    var subjectError by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val result = AttachmentHelper.copyToInternalStorage(context, uri)
            if (result != null) {
                if (attachmentPath.isNotBlank() && attachmentPath != initial?.attachmentPath) {
                    AttachmentHelper.deleteAttachment(attachmentPath)
                }
                attachmentPath = result.first
                attachmentName = result.second
                createdAttachmentPaths.add(result.first)
            } else {
                Toast.makeText(context, "Datei konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeAttachment() {
        if (attachmentPath.isNotBlank() && attachmentPath != initial?.attachmentPath) {
            AttachmentHelper.deleteAttachment(attachmentPath)
        }
        attachmentPath = ""
        attachmentName = ""
    }

    val dismissWithCleanup = {
        createdAttachmentPaths.forEach { path ->
            if (path != initial?.attachmentPath) {
                AttachmentHelper.deleteAttachment(path)
            }
        }
        onDismiss()
    }

    fun pickDateTime() {
        showDatePicker = true
    }

    AlertDialog(
        onDismissRequest = dismissWithCleanup,
        confirmButton = {
            Button(
                onClick = {
                    toError      = recipientTo.isBlank()
                    subjectError = subject.isBlank()
                    if (toError || subjectError) return@Button
                    val intervalVal = intervalMinutes.toDoubleOrNull() ?: 60.0
                    if (scheduleType == ScheduleType.PERIODIC && intervalVal < 0.1) {
                        Toast.makeText(context, "Das Mindestintervall beträgt 0.1 Minuten", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (initial != null && initial.attachmentPath.isNotBlank() && attachmentPath != initial.attachmentPath) {
                        AttachmentHelper.deleteAttachment(initial.attachmentPath)
                    }
                    val task = (initial ?: EmailTask()).copy(
                        label           = label,
                        recipientTo     = recipientTo.trim(),
                        recipientCc     = recipientCc.trim(),
                        recipientBcc    = recipientBcc.trim(),
                        subject         = subject,
                        body            = body,
                        scheduleType    = scheduleType,
                        scheduledTimeMs = scheduledMs,
                        intervalMinutes = intervalVal,
                        cronDayOfWeek   = cronDayOfWeek,
                        cronHour        = cronHour,
                        cronMinute      = cronMinute,
                        smtpAccountId   = selectedAccountId,
                        isEnabled       = true,
                        status          = EmailStatus.PENDING,
                        lastErrorMessage = "",
                        attachmentPath  = attachmentPath,
                        attachmentName  = attachmentName,
                        requireWifi     = requireWifi,
                        requireCharging = requireCharging,
                        requireMinBattery = requireMinBattery
                    )
                    onSave(task)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) { Text(if (initial == null) "Hinzufügen" else "Speichern") }
        },
        dismissButton = { TextButton(onClick = dismissWithCleanup) { Text("Abbrechen") } },
        title = {
            Text(
                if (initial == null) "Neue E-Mail planen" else "E-Mail bearbeiten",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Bezeichnung (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                OutlinedTextField(
                    value = recipientTo,
                    onValueChange = { recipientTo = it; toError = false },
                    label = { Text("An *") },
                    placeholder = { Text("max@beispiel.de, anna@firma.de") },
                    singleLine = true,
                    isError = toError,
                    supportingText = { if (toError) Text("Mindestens ein Empfänger erforderlich") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                TextButton(
                    onClick = { showCcBcc = !showCcBcc },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(if (showCcBcc) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CC / BCC", fontSize = 12.sp)
                }

                AnimatedVisibility(
                    visible = showCcBcc,
                    enter = expandVertically() + fadeIn(),
                    exit  = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = recipientCc,
                            onValueChange = { recipientCc = it },
                            label = { Text("CC") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        OutlinedTextField(
                            value = recipientBcc,
                            onValueChange = { recipientBcc = it },
                            label = { Text("BCC") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                    }
                }

                HorizontalDivider()

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it; subjectError = false },
                    label = { Text("Betreff *") },
                    singleLine = true,
                    isError = subjectError,
                    supportingText = { if (subjectError) Text("Betreff ist erforderlich") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Nachrichtentext") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                Text("Anhang", style = MaterialTheme.typography.labelLarge)
                if (attachmentName.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = attachmentName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { removeAttachment() }) {
                                Icon(Icons.Default.Close, "Entfernen", tint = ColorFailed)
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Datei anhängen")
                    }
                }

                HorizontalDivider()

                Text("SMTP Absender-Konto", style = MaterialTheme.typography.labelLarge)
                if (accounts.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Kein SMTP-Konto konfiguriert! Bitte erstelle zuerst ein SMTP-Konto in den Einstellungen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    val currentAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()

                    LaunchedEffect(accounts) {
                        if (selectedAccountId == 0 && accounts.isNotEmpty()) {
                            selectedAccountId = accounts.first().id
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Email, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(currentAccount?.let { "${it.accountName} (${it.username})" } ?: "Konto auswählen", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.accountName} (${acc.username})") },
                                    onClick = {
                                        selectedAccountId = acc.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text("Sendezeit", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scheduleType == ScheduleType.ONCE,
                        onClick = { scheduleType = ScheduleType.ONCE },
                        label = { Text("Einmalig") },
                        leadingIcon = if (scheduleType == ScheduleType.ONCE) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = scheduleType == ScheduleType.PERIODIC,
                        onClick = { scheduleType = ScheduleType.PERIODIC },
                        label = { Text("Periodisch") },
                        leadingIcon = if (scheduleType == ScheduleType.PERIODIC) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = scheduleType == ScheduleType.CRON,
                        onClick = { scheduleType = ScheduleType.CRON },
                        label = { Text("Wochentag") },
                        leadingIcon = if (scheduleType == ScheduleType.CRON) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                }

                if (scheduleType == ScheduleType.ONCE) {
                    OutlinedButton(
                        onClick = { pickDateTime() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(sdf.format(Date(scheduledMs)))
                    }
                } else if (scheduleType == ScheduleType.PERIODIC) {
                    Text("Erstmalig senden:", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { pickDateTime() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(sdf.format(Date(scheduledMs)))
                    }
                    OutlinedTextField(
                        value = intervalMinutes,
                        onValueChange = { intervalMinutes = it.filter { c -> c.isDigit() || c == '.' || c == '.' }.replace(',', '.') },
                        label = { Text("Intervall (Minuten)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text("Erlaubt sind Dezimalwerte wie z.B. 0.5") }
                    )
                } else if (scheduleType == ScheduleType.CRON) {
                    Text("Wochentag auswählen:", style = MaterialTheme.typography.bodySmall)
                    val days = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 1, 2, 3).forEach { dayIdx ->
                                FilterChip(
                                    selected = cronDayOfWeek == dayIdx,
                                    onClick = { cronDayOfWeek = dayIdx },
                                    label = { Text(days[dayIdx], fontSize = 12.sp) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(4, 5, 6).forEach { dayIdx ->
                                FilterChip(
                                    selected = cronDayOfWeek == dayIdx,
                                    onClick = { cronDayOfWeek = dayIdx },
                                    label = { Text(days[dayIdx], fontSize = 12.sp) }
                                )
                            }
                            FilterChip(
                                selected = cronDayOfWeek == -1,
                                onClick = { cronDayOfWeek = -1 },
                                label = { Text("Täglich", fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Uhrzeit auswählen:", style = MaterialTheme.typography.bodySmall)
                    var showCronTimePicker by remember { mutableStateOf(false) }
                    val timeStr = String.format(Locale.getDefault(), "%02d:%02d Uhr", cronHour, cronMinute)
                    OutlinedButton(
                        onClick = { showCronTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AccessTime, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(timeStr)
                    }

                    if (showCronTimePicker) {
                        val timePickerState = rememberTimePickerState(
                            initialHour = cronHour,
                            initialMinute = cronMinute,
                            is24Hour = true
                        )
                        AlertDialog(
                            onDismissRequest = { showCronTimePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    cronHour = timePickerState.hour
                                    cronMinute = timePickerState.minute
                                    showCronTimePicker = false
                                }) { Text("Fertig") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCronTimePicker = false }) { Text("Abbrechen") }
                            },
                            title = { Text("Uhrzeit auswählen") },
                            text = {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TimePicker(state = timePickerState)
                                }
                            }
                        )
                    }
                }

                HorizontalDivider()

                var showTriggers by remember { mutableStateOf(requireWifi || requireCharging || requireMinBattery > 0) }
                TextButton(
                    onClick = { showTriggers = !showTriggers },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(if (showTriggers) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Bedingungen (Triggers)", fontSize = 12.sp)
                }

                AnimatedVisibility(
                    visible = showTriggers,
                    enter = expandVertically() + fadeIn(),
                    exit  = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Nur im WLAN senden", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("Spart mobile Daten", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = requireWifi, onCheckedChange = { requireWifi = it })
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Nur beim Laden senden", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("Schont die Batterie im Standby", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = requireCharging, onCheckedChange = { requireCharging = it })
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Mindest-Akkustand", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = if (requireMinBattery > 0) "Senden erst ab $requireMinBattery%" else "Keine Akku-Beschränkung",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Slider(
                                    value = requireMinBattery.toFloat(),
                                    onValueChange = { requireMinBattery = it.toInt() },
                                    valueRange = 0f..100f,
                                    steps = 19,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = scheduledMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = datePickerState.selectedDateMillis ?: scheduledMs
                    val c = Calendar.getInstance().apply { timeInMillis = selectedDate }
                    val targetCal = Calendar.getInstance().apply { timeInMillis = scheduledMs }
                    targetCal.set(Calendar.YEAR, c.get(Calendar.YEAR))
                    targetCal.set(Calendar.MONTH, c.get(Calendar.MONTH))
                    targetCal.set(Calendar.DAY_OF_MONTH, c.get(Calendar.DAY_OF_MONTH))
                    scheduledMs = targetCal.timeInMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Weiter") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val targetCal = Calendar.getInstance().apply { timeInMillis = scheduledMs }
        val timePickerState = rememberTimePickerState(
            initialHour = targetCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = targetCal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    targetCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    targetCal.set(Calendar.MINUTE, timePickerState.minute)
                    targetCal.set(Calendar.SECOND, 0)
                    targetCal.set(Calendar.MILLISECOND, 0)
                    scheduledMs = targetCal.timeInMillis
                    showTimePicker = false
                }) { Text("Fertig") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Zurück") }
            },
            title = { Text("Uhrzeit auswählen") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }
}
