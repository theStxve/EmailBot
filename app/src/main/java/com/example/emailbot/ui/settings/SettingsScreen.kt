package com.example.emailbot.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emailbot.data.SecurityType
import com.example.emailbot.data.SmtpSettings

private val gradientHeader = Brush.linearGradient(
    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    accountId: Int?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val saveResult by vm.saveResult.collectAsStateWithLifecycle()
    val isTesting by vm.isTesting.collectAsStateWithLifecycle()
    val testResult by vm.testResult.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(accountId) {
        vm.loadAccount(accountId)
    }

    LaunchedEffect(saveResult) {
        if (saveResult != null) {
            kotlinx.coroutines.delay(2000)
            vm.clearSaveResult()
            onBack()
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
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = Color.White)
                    }
                    Column {
                        Text(
                            text = if (accountId == null || accountId == 0) "Neues SMTP-Konto" else "Konto bearbeiten",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Ausgehendes E-Mail Konto konfigurieren", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (saveResult != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF22C55E).copy(alpha = 0.15f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E))
                        Spacer(Modifier.width(8.dp))
                        Text(saveResult ?: "", color = Color(0xFF22C55E), fontWeight = FontWeight.Medium)
                    }
                }
            }

            SectionHeader("Konto")

            OutlinedTextField(
                value = settings.accountName,
                onValueChange = { vm.updateSettings(settings.copy(accountName = it)) },
                label = { Text("Konto-Name (z.B. GMX Privat)") },
                placeholder = { Text("Mein SMTP-Konto") },
                leadingIcon = { Icon(Icons.Default.Bookmark, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = settings.fromName,
                onValueChange = { vm.updateSettings(settings.copy(fromName = it)) },
                label = { Text("Absender Name") },
                placeholder = { Text("Max Mustermann") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = settings.username,
                onValueChange = { vm.updateSettings(settings.copy(username = it)) },
                label = { Text("E-Mail Adresse / Benutzername") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = settings.password,
                onValueChange = { vm.updateSettings(settings.copy(password = it)) },
                label = { Text("Passwort / App-Passwort") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader("Server")

            OutlinedTextField(
                value = settings.host,
                onValueChange = { vm.updateSettings(settings.copy(host = it)) },
                label = { Text("SMTP Server") },
                placeholder = { Text("smtp.gmail.com") },
                leadingIcon = { Icon(Icons.Default.Dns, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = settings.port.toString(),
                onValueChange = { vm.updateSettings(settings.copy(port = it.toIntOrNull() ?: settings.port)) },
                label = { Text("Port") },
                leadingIcon = { Icon(Icons.Default.Lan, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader("Sicherheit")

            SecurityType.entries.forEach { sec ->
                val (label, hint) = when (sec) {
                    SecurityType.STARTTLS -> "STARTTLS" to "Empfohlen · Port 587"
                    SecurityType.SSL_TLS  -> "SSL / TLS" to "Sicher · Port 465"
                    SecurityType.NONE     -> "Keine"     to "Nicht empfohlen"
                }
                Card(
                    onClick = { vm.updateSettings(settings.copy(security = sec)) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.security == sec)
                            Color(0xFF6366F1).copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    border = if (settings.security == sec)
                        CardDefaults.outlinedCardBorder().copy()
                    else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = settings.security == sec, onClick = { vm.updateSettings(settings.copy(security = sec)) })
                        Column(Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.Medium)
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            SectionHeader("Schnelleinstellung")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmtpPresetButton("Gmail") {
                    vm.updateSettings(settings.copy(host = "smtp.gmail.com", port = 587, security = SecurityType.STARTTLS))
                }
                SmtpPresetButton("Outlook") {
                    vm.updateSettings(settings.copy(host = "smtp-mail.outlook.com", port = 587, security = SecurityType.STARTTLS))
                }
                SmtpPresetButton("GMX") {
                    vm.updateSettings(settings.copy(host = "mail.gmx.net", port = 587, security = SecurityType.STARTTLS))
                }
            }

            val username = settings.username.lowercase()
            val adviceText = if (username.contains("@gmx") || username.contains("@web.de")) {
                "Für GMX & Web.de: Schalte in deinem E-Mail-Postfach im Browser unter 'POP3 & IMAP' die Option 'IMAP und SMTP erlauben' frei. Andernfalls blockiert der Anbieter den Zugriff."
            } else if (username.contains("@gmail") || username.contains("@outlook") || username.contains("@hotmail") || username.contains("@live")) {
                "Für Gmail & Outlook: Aktiviere die 2-Faktor-Authentifizierung in deinem E-Mail-Konto und erstelle ein App-Passwort. Trage dieses App-Passwort anstelle deines normalen Passworts ein."
            } else {
                "Tipp: Die SMTP-Einstellungen werden für bekannte Domains (GMX, Web.de, Gmail, Outlook) beim Eintippen der E-Mail-Adresse automatisch ausgefüllt."
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF6366F1).copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = adviceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6366F1)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isTesting) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF6366F1))
                        Spacer(Modifier.width(12.dp))
                        Text("Verbindung wird getestet...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                testResult?.let { result ->
                    val isOk = result.contains("erfolgreich")
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOk) Color(0xFF22C55E).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isOk) Color(0xFF22C55E) else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = result,
                                color = if (isOk) Color(0xFF22C55E) else MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { vm.testConnection() },
                enabled = !isTesting && settings.username.isNotBlank() && settings.password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Bolt, null)
                Spacer(Modifier.width(8.dp))
                Text("Verbindung testen")
            }

            Button(
                onClick = { vm.save() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Konto speichern", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFF6366F1),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SmtpPresetButton(name: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        Text(name, fontSize = 12.sp)
    }
}
