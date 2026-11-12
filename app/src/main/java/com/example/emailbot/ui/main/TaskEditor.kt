package com.example.emailbot.ui.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.emailbot.data.EmailStatus
import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.MIN_INTERVAL_MINUTES
import com.example.emailbot.data.ScheduleType
import com.example.emailbot.data.SmtpSettings
import com.example.emailbot.data.displayName
import com.example.emailbot.mail.SmtpClient
import com.example.emailbot.ui.WEEKDAYS_SHORT
import com.example.emailbot.ui.formatDateLong
import com.example.emailbot.ui.formatInterval
import com.example.emailbot.ui.formatTime
import com.example.emailbot.util.AttachmentHelper
import java.util.Calendar
import java.util.TimeZone

/** Full-screen editor for a new ([initial] == null) or existing task. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditor(
    initial: EmailTask?,
    accounts: List<SmtpSettings>,
    onSave: (EmailTask) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var label by rememberSaveable { mutableStateOf(initial?.label ?: "") }
    var recipientTo by rememberSaveable { mutableStateOf(initial?.recipientTo ?: "") }
    var recipientCc by rememberSaveable { mutableStateOf(initial?.recipientCc ?: "") }
    var recipientBcc by rememberSaveable { mutableStateOf(initial?.recipientBcc ?: "") }
    var showCcBcc by rememberSaveable { mutableStateOf(recipientCc.isNotBlank() || recipientBcc.isNotBlank()) }
    var subject by rememberSaveable { mutableStateOf(initial?.subject ?: "") }
    var body by rememberSaveable { mutableStateOf(initial?.body ?: "") }

    var scheduleType by rememberSaveable { mutableStateOf(initial?.scheduleType ?: ScheduleType.ONCE) }
    var scheduledMs by rememberSaveable {
        mutableLongStateOf(
            initial?.scheduledTimeMs?.takeIf { it > 0 } ?: Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    var intervalText by rememberSaveable {
        val v = initial?.intervalMinutes ?: 60.0
        mutableStateOf(if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ','))
    }
    var cronDay by rememberSaveable { mutableIntStateOf(initial?.cronDayOfWeek ?: 0) }
    var cronHour by rememberSaveable { mutableIntStateOf(initial?.cronHour ?: 9) }
    var cronMinute by rememberSaveable { mutableIntStateOf(initial?.cronMinute ?: 0) }

    var accountId by rememberSaveable { mutableIntStateOf(initial?.smtpAccountId ?: 0) }
    LaunchedEffect(accounts) {
        // Fall back to the default account if none is chosen or the chosen one was deleted.
        if (accounts.isNotEmpty() && accounts.none { it.id == accountId }) accountId = accounts.first().id
    }

    var attachmentPath by rememberSaveable { mutableStateOf(initial?.attachmentPath ?: "") }
    var attachmentName by rememberSaveable { mutableStateOf(initial?.attachmentName ?: "") }
    val newAttachments = remember { mutableStateListOf<String>() }

    var requireWifi by rememberSaveable { mutableStateOf(initial?.requireWifi ?: false) }
    var requireCharging by rememberSaveable { mutableStateOf(initial?.requireCharging ?: false) }
    var requireMinBattery by rememberSaveable { mutableIntStateOf(initial?.requireMinBattery ?: 0) }
    var showConditions by rememberSaveable { mutableStateOf(requireWifi || requireCharging || requireMinBattery > 0) }

    var showErrors by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCronTimePicker by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val intervalValue = intervalText.replace(',', '.').toDoubleOrNull()

    // ── Validation ──
    val toError = when {
        recipientTo.isBlank() -> "Mindestens ein Empfänger"
        !SmtpClient.isValidAddressList(recipientTo) -> "Ungültige Adresse. Mehrere mit Komma trennen."
        else -> null
    }
    val ccError = if (recipientCc.isNotBlank() && !SmtpClient.isValidAddressList(recipientCc)) "Ungültige Adresse" else null
    val bccError = if (recipientBcc.isNotBlank() && !SmtpClient.isValidAddressList(recipientBcc)) "Ungültige Adresse" else null
    val subjectError = if (subject.isBlank()) "Betreff fehlt" else null
    val timeError = if (scheduleType == ScheduleType.ONCE && scheduledMs <= System.currentTimeMillis()) "Dieser Zeitpunkt liegt in der Vergangenheit" else null
    val intervalError = if (scheduleType == ScheduleType.PERIODIC && (intervalValue == null || intervalValue < MIN_INTERVAL_MINUTES))
        "Mindestens ${MIN_INTERVAL_MINUTES.toInt()} Minute" else null
    val accountError = if (accounts.isEmpty()) "Kein E-Mail-Konto eingerichtet" else null
    val isValid = listOf(toError, ccError, bccError, subjectError, timeError, intervalError, accountError).all { it == null }

    fun buildTask() = (initial ?: EmailTask()).copy(
        label = label.trim(),
        recipientTo = recipientTo.trim(),
        recipientCc = if (showCcBcc) recipientCc.trim() else "",
        recipientBcc = if (showCcBcc) recipientBcc.trim() else "",
        subject = subject.trim(),
        body = body,
        scheduleType = scheduleType,
        scheduledTimeMs = scheduledMs,
        intervalMinutes = intervalValue ?: 60.0,
        cronDayOfWeek = cronDay,
        cronHour = cronHour,
        cronMinute = cronMinute,
        smtpAccountId = accountId,
        attachmentPath = attachmentPath,
        attachmentName = attachmentName,
        requireWifi = requireWifi,
        requireCharging = requireCharging,
        requireMinBattery = requireMinBattery,
        // Saving (re)activates the task with a clean slate.
        isEnabled = true,
        status = EmailStatus.PENDING,
        lastErrorMessage = ""
    )

    val baseline = remember { buildTask().copy(smtpAccountId = 0) }
    val isDirty = buildTask().copy(smtpAccountId = 0) != baseline

    fun discard() {
        newAttachments.filter { it != initial?.attachmentPath }.forEach(AttachmentHelper::deleteAttachment)
        onDismiss()
    }

    fun requestClose() {
        if (isDirty) confirmDiscard = true else discard()
    }

    fun save() {
        showErrors = true
        if (!isValid) {
            Toast.makeText(context, "Bitte die markierten Felder prüfen", Toast.LENGTH_SHORT).show()
            return
        }
        // Clean up attachment files that are no longer used.
        initial?.attachmentPath?.takeIf { it.isNotBlank() && it != attachmentPath }?.let(AttachmentHelper::deleteAttachment)
        newAttachments.filter { it != attachmentPath }.forEach(AttachmentHelper::deleteAttachment)
        onSave(buildTask())
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = AttachmentHelper.copyToInternalStorage(context, uri)
        if (result == null) {
            Toast.makeText(context, "Datei konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
        } else {
            attachmentPath = result.first
            attachmentName = result.second
            newAttachments += result.first
        }
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // The dialog has its own window: give it status bar icons that contrast with the light/dark surface.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightBars = !isSystemInDarkTheme()
        SideEffect {
            dialogWindow?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = lightBars }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (initial == null) "Neue E-Mail" else "E-Mail bearbeiten") },
                    navigationIcon = {
                        IconButton(onClick = ::requestClose) { Icon(Icons.Default.Close, contentDescription = "Schließen") }
                    },
                    actions = {
                        Button(onClick = ::save, modifier = Modifier.padding(end = 8.dp)) {
                            Text(if (initial == null) "Planen" else "Speichern")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Message ──
                EditorSection("Nachricht") {
                    OutlinedTextField(
                        value = recipientTo,
                        onValueChange = { recipientTo = it },
                        label = { Text("An") },
                        placeholder = { Text("anna@beispiel.de, max@firma.de") },
                        singleLine = true,
                        isError = showErrors && toError != null,
                        supportingText = if (showErrors && toError != null) { { Text(toError) } } else null,
                        trailingIcon = {
                            TextButton(onClick = { showCcBcc = !showCcBcc }) { Text(if (showCcBcc) "−CC" else "CC/BCC") }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AnimatedVisibility(showCcBcc) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = recipientCc, onValueChange = { recipientCc = it },
                                label = { Text("CC") }, singleLine = true,
                                isError = showErrors && ccError != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = recipientBcc, onValueChange = { recipientBcc = it },
                                label = { Text("BCC") }, singleLine = true,
                                isError = showErrors && bccError != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Betreff") },
                        singleLine = true,
                        isError = showErrors && subjectError != null,
                        supportingText = if (showErrors && subjectError != null) { { Text(subjectError) } } else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Nachricht") },
                        minLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (attachmentName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(start = 12.dp)
                        ) {
                            Icon(Icons.Outlined.AttachFile, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(attachmentName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { attachmentPath = ""; attachmentName = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Anhang entfernen")
                            }
                        }
                    } else {
                        OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Datei anhängen")
                        }
                    }
                }

                // ── Schedule ──
                EditorSection("Wann senden?") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(
                            ScheduleType.ONCE to "Einmalig",
                            ScheduleType.PERIODIC to "Intervall",
                            ScheduleType.CRON to "Wöchentlich"
                        ).forEachIndexed { index, (type, text) ->
                            SegmentedButton(
                                selected = scheduleType == type,
                                onClick = { scheduleType = type },
                                shape = SegmentedButtonDefaults.itemShape(index, 3)
                            ) { Text(text, maxLines = 1) }
                        }
                    }

                    when (scheduleType) {
                        ScheduleType.ONCE, ScheduleType.PERIODIC -> {
                            if (scheduleType == ScheduleType.PERIODIC) {
                                Text("Erster Versand", style = MaterialTheme.typography.labelLarge)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PickerButton(Icons.Outlined.CalendarMonth, formatDateLong(scheduledMs), Modifier.weight(1.6f)) { showDatePicker = true }
                                PickerButton(Icons.Outlined.Schedule, formatTime(scheduledMs), Modifier.weight(1f)) { showTimePicker = true }
                            }
                            if (showErrors && timeError != null) ErrorText(timeError)
                            if (scheduleType == ScheduleType.PERIODIC) {
                                OutlinedTextField(
                                    value = intervalText,
                                    onValueChange = { new -> intervalText = new.filter { it.isDigit() || it == ',' || it == '.' } },
                                    label = { Text("Danach alle") },
                                    suffix = { Text("Minuten") },
                                    singleLine = true,
                                    isError = showErrors && intervalError != null,
                                    supportingText = {
                                        when {
                                            showErrors && intervalError != null -> Text(intervalError)
                                            intervalValue != null && intervalValue >= MIN_INTERVAL_MINUTES -> Text("= alle ${formatInterval(intervalValue)}")
                                            else -> {}
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (intervalValue != null && intervalValue in MIN_INTERVAL_MINUTES..<15.0) {
                                    Text(
                                        "Hinweis: Im Energiesparmodus kann Android sehr kurze Intervalle auf etwa 10 Minuten strecken.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        ScheduleType.CRON -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                WEEKDAYS_SHORT.forEachIndexed { index, day ->
                                    DayToggle(day, selected = cronDay == index, Modifier.weight(1f)) { cronDay = index }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = cronDay == -1, onCheckedChange = { cronDay = if (it) -1 else 0 })
                                Spacer(Modifier.width(12.dp))
                                Text("Jeden Tag")
                            }
                            PickerButton(Icons.Outlined.Schedule, "um ${formatTime(cronHour, cronMinute)} Uhr", Modifier.fillMaxWidth()) {
                                showCronTimePicker = true
                            }
                        }
                    }
                }

                // ── Sender ──
                EditorSection("Absender") {
                    when {
                        accounts.isEmpty() -> ErrorText("Kein E-Mail-Konto eingerichtet. Lege zuerst eines an.")
                        else -> {
                            var expanded by remember { mutableStateOf(false) }
                            val current = accounts.find { it.id == accountId } ?: accounts.first()
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && accounts.size > 1 }) {
                                OutlinedTextField(
                                    value = "${current.displayName} · ${current.username}",
                                    onValueChange = {},
                                    readOnly = true,
                                    singleLine = true,
                                    label = { Text("Senden über") },
                                    trailingIcon = { if (accounts.size > 1) ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    accounts.forEach { acc ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(acc.displayName)
                                                    Text(acc.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            },
                                            onClick = { accountId = acc.id; expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Name in der Liste (optional)") },
                        placeholder = { Text(subject.ifBlank { "z. B. Wochenbericht" }) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Conditions ──
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showConditions = !showConditions }
                            .padding(16.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Bedingungen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    "WLAN".takeIf { requireWifi },
                                    "Laden".takeIf { requireCharging },
                                    "ab $requireMinBattery % Akku".takeIf { requireMinBattery > 0 }
                                ).joinToString(" · ").ifBlank { "Keine – wird immer gesendet" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(if (showConditions) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    }
                    AnimatedVisibility(showConditions) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            Text(
                                "Ist eine Bedingung nicht erfüllt, wartet die E-Mail, bis sie es ist.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ConditionSwitch(Icons.Outlined.Wifi, "Nur im WLAN", requireWifi) { requireWifi = it }
                            ConditionSwitch(Icons.Outlined.BatteryChargingFull, "Nur beim Laden", requireCharging) { requireCharging = it }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                Icon(Icons.Outlined.BatteryStd, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    if (requireMinBattery > 0) "Mindestens $requireMinBattery % Akku" else "Kein Mindest-Akkustand",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Slider(
                                value = requireMinBattery.toFloat(),
                                onValueChange = { requireMinBattery = (it / 5).toInt() * 5 },
                                valueRange = 0f..90f,
                                steps = 17
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        // ── Pickers & confirmations (inside the Dialog so they draw above it) ──
        if (showDatePicker) {
            val todayUtc = remember {
                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    val local = Calendar.getInstance()
                    clear()
                    set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
                }.timeInMillis
            }
            val initialUtc = remember(scheduledMs) {
                val local = Calendar.getInstance().apply { timeInMillis = scheduledMs }
                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    clear()
                    set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
                }.timeInMillis
            }
            val state = rememberDatePickerState(
                initialSelectedDateMillis = initialUtc,
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
                }
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { utc ->
                            // The picker returns midnight UTC of the chosen day; keep our local time of day.
                            val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                            scheduledMs = Calendar.getInstance().apply {
                                timeInMillis = scheduledMs
                                set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH))
                            }.timeInMillis
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } }
            ) { DatePicker(state = state) }
        }

        if (showTimePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = scheduledMs }
            TimePickerDialog(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), onDismiss = { showTimePicker = false }) { h, m ->
                scheduledMs = cal.apply {
                    set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }

        if (showCronTimePicker) {
            TimePickerDialog(cronHour, cronMinute, onDismiss = { showCronTimePicker = false }) { h, m ->
                cronHour = h; cronMinute = m
            }
        }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text("Änderungen verwerfen?") },
                text = { Text("Deine Eingaben gehen verloren.") },
                confirmButton = { TextButton(onClick = { confirmDiscard = false; discard() }) { Text("Verwerfen") } },
                dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Weiter bearbeiten") } }
            )
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun PickerButton(icon: ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(12.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DayToggle(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConditionSwitch(icon: ImageVector, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ErrorText(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uhrzeit") },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
