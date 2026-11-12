package com.example.emailbot.ui.main

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emailbot.ui.appViewModelFactory
import com.example.emailbot.data.EmailStatus
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.ScheduleType
import com.example.emailbot.data.displayTitle
import com.example.emailbot.data.isActive
import com.example.emailbot.theme.GradientTopBar
import com.example.emailbot.ui.formatRelative

@Composable
fun MainScreen(
    onOpenAccounts: () -> Unit,
    vm: MainViewModel = viewModel(factory = appViewModelFactory())
) {
    val context = LocalContext.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingTaskId by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<EmailTask?>(null) }
    var pendingSend by remember { mutableStateOf<EmailTask?>(null) }
    var confirmClearLog by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importTasks(uri)
    }

    val hasAccounts = !accounts.isNullOrEmpty()
    val activeCount = tasks?.count { it.isActive } ?: 0

    fun openEditor(task: EmailTask?) {
        editingTaskId = task?.id ?: 0
        editorOpen = true
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "EmailBot",
                subtitle = when {
                    tasks == null -> " "
                    activeCount == 0 -> "Keine aktiven Zeitpläne"
                    activeCount == 1 -> "1 aktiver Zeitplan"
                    else -> "$activeCount aktive Zeitpläne"
                }
            ) {
                IconButton(onClick = onOpenAccounts) {
                    Icon(Icons.Outlined.AlternateEmail, contentDescription = "E-Mail-Konten", tint = Color.White)
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Weitere Optionen", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Sicherung exportieren") },
                            leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                            enabled = !tasks.isNullOrEmpty(),
                            onClick = {
                                menuOpen = false
                                vm.exportTasks { uri ->
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Sicherung speichern oder teilen"))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sicherung importieren") },
                            leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                            onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 && hasAccounts && !tasks.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { openEditor(null) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Neue E-Mail") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                    label = { Text("Geplant") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    label = { Text("Verlauf") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when (selectedTab) {
            0 -> TaskListTab(
                tasks = tasks,
                accounts = accounts,
                onOpenAccounts = onOpenAccounts,
                onNewTask = { openEditor(null) },
                onEdit = { openEditor(it) },
                onToggle = vm::toggleEnabled,
                onDelete = { pendingDelete = it },
                onSendNow = { pendingSend = it },
                modifier = modifier
            )
            else -> HistoryTab(
                logs = logs,
                onClear = { confirmClearLog = true },
                modifier = modifier
            )
        }
    }

    if (editorOpen) {
        val initial = tasks?.find { it.id == editingTaskId }
        if (editingTaskId == 0 || initial != null) {
            TaskEditor(
                initial = initial,
                accounts = accounts.orEmpty(),
                onSave = { vm.saveTask(it); editorOpen = false },
                onDismiss = { editorOpen = false }
            )
        }
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("E-Mail löschen?") },
            text = { Text("„${task.displayTitle}“ und der zugehörige Zeitplan werden gelöscht.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTask(task); pendingDelete = null }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Abbrechen") } }
        )
    }

    pendingSend?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingSend = null },
            icon = { Icon(Icons.AutoMirrored.Outlined.Send, null) },
            title = { Text("Jetzt senden?") },
            text = { Text(sendNowExplanation(task)) },
            confirmButton = {
                TextButton(onClick = { vm.sendNow(task); pendingSend = null }) { Text("Senden") }
            },
            dismissButton = { TextButton(onClick = { pendingSend = null }) { Text("Abbrechen") } }
        )
    }

    if (confirmClearLog) {
        AlertDialog(
            onDismissRequest = { confirmClearLog = false },
            title = { Text("Verlauf leeren?") },
            text = { Text("Alle Einträge im Verlauf werden gelöscht. Deine geplanten E-Mails bleiben erhalten.") },
            confirmButton = {
                TextButton(onClick = { vm.clearLogs(); confirmClearLog = false }) {
                    Text("Leeren", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearLog = false }) { Text("Abbrechen") } }
        )
    }
}

private fun sendNowExplanation(task: EmailTask): String = buildString {
    append("Die E-Mail geht sofort an ${task.recipientTo}.")
    when {
        task.scheduleType != ScheduleType.ONCE -> append(" Der Zeitplan läuft danach normal weiter.")
        task.status == EmailStatus.SENT -> append(" Sie wurde bereits gesendet und geht damit ein weiteres Mal raus.")
        task.isEnabled -> append(" Der geplante Versand (${formatRelative(task.scheduledTimeMs)}) entfällt dann.")
    }
    if (task.requireWifi || task.requireCharging || task.requireMinBattery > 0) {
        append(" Die Sendebedingungen werden dabei ignoriert.")
    }
}
