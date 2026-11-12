package com.example.emailbot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emailbot.ui.appViewModelFactory
import com.example.emailbot.data.SecurityType
import com.example.emailbot.mail.MailProviders
import com.example.emailbot.theme.GradientTopBar
import com.example.emailbot.theme.StatusFailed
import com.example.emailbot.theme.StatusPending
import com.example.emailbot.theme.StatusSuccess

/** Create or edit one SMTP account. */
@Composable
fun SettingsScreen(
    accountId: Int?,
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = appViewModelFactory())
) {
    LaunchedEffect(accountId) { vm.load(accountId) }
    LaunchedEffect(Unit) { vm.saved.collect { onBack() } }

    val form by vm.form.collectAsStateWithLifecycle()
    val portText by vm.portText.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val errors = if (showErrors) vm.validate() else emptyMap()
    val provider = MailProviders.forAddress(form.username) ?: MailProviders.forHost(form.host)

    Scaffold(
        topBar = {
            GradientTopBar(
                title = if (accountId == null) "Neues Konto" else "Konto bearbeiten",
                subtitle = provider?.let { "${it.name} erkannt" } ?: "SMTP-Zugang für den Versand",
                onBack = onBack
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { showErrors = true; vm.testConnection() },
                        enabled = test != TestState.Running,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("Verbindung testen") }
                    Button(
                        onClick = { showErrors = true; vm.save() },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("Speichern") }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val t = test) {
                TestState.Idle -> Unit
                TestState.Running -> Banner(null, "Verbinde mit ${form.host} …", MaterialTheme.colorScheme.primary)
                TestState.Success -> Banner(Icons.Default.CheckCircle, "Verbindung erfolgreich. Das Konto ist startklar.", StatusSuccess)
                is TestState.Failed -> Banner(Icons.Outlined.ErrorOutline, t.message, StatusFailed)
            }

            Section("Anmeldung") {
                OutlinedTextField(
                    value = form.username,
                    onValueChange = { v -> vm.update { it.copy(username = v) } },
                    label = { Text("E-Mail-Adresse") },
                    placeholder = { Text("du@beispiel.de") },
                    singleLine = true,
                    isError = "username" in errors,
                    supportingText = errors["username"]?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { v -> vm.update { it.copy(password = v) } },
                    label = { Text(if (provider != null) "App-Passwort" else "Passwort") },
                    singleLine = true,
                    isError = "password" in errors,
                    supportingText = errors["password"]?.let { { Text(it) } },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Passwort verbergen" else "Passwort anzeigen"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                if (provider != null) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Lightbulb, null, tint = StatusPending, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(provider.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Section("Absender") {
                OutlinedTextField(
                    value = form.fromName,
                    onValueChange = { v -> vm.update { it.copy(fromName = v) } },
                    label = { Text("Angezeigter Name (optional)") },
                    placeholder = { Text("Max Mustermann") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.accountName,
                    onValueChange = { v -> vm.update { it.copy(accountName = v) } },
                    label = { Text("Name des Kontos in der App (optional)") },
                    placeholder = { Text(provider?.let { "${it.name} privat" } ?: "Arbeit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Section("Server") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.host,
                        onValueChange = { v -> vm.update { it.copy(host = v) } },
                        label = { Text("SMTP-Server") },
                        placeholder = { Text("smtp.beispiel.de") },
                        singleLine = true,
                        isError = "host" in errors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = vm::updatePort,
                        label = { Text("Port") },
                        singleLine = true,
                        isError = "port" in errors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp)
                    )
                }
                Text("Verschlüsselung", style = MaterialTheme.typography.labelLarge)
                val options = listOf(
                    Triple(SecurityType.STARTTLS, "STARTTLS", 587),
                    Triple(SecurityType.SSL_TLS, "SSL/TLS", 465),
                    Triple(SecurityType.NONE, "Keine", 25)
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (type, label, defaultPort) ->
                        SegmentedButton(
                            selected = form.security == type,
                            onClick = {
                                vm.update { it.copy(security = type) }
                                // Switching encryption usually means switching to its standard port.
                                if (portText in listOf("25", "465", "587", "")) vm.updatePort(defaultPort.toString())
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size)
                        ) { Text(label) }
                    }
                }
                if (form.security == SecurityType.NONE) {
                    Text(
                        "Ohne Verschlüsselung gehen Passwort und E-Mail im Klartext durchs Netz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusPending
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun Banner(icon: ImageVector?, text: String, color: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, tint = color)
            else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
