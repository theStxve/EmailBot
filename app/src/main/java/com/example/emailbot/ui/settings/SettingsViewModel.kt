package com.example.emailbot.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.emailbot.data.SecurityType
import com.example.emailbot.data.SmtpSettings
import com.example.emailbot.data.SmtpSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.PasswordAuthentication
import javax.mail.Session

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SmtpSettingsRepository(application)

    val accounts: StateFlow<List<SmtpSettings>> = repo.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _settings = MutableStateFlow(SmtpSettings())
    val settings: StateFlow<SmtpSettings> = _settings.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    init {
        repo.migrateLegacyIfNeeded(application)
    }

    fun loadAccount(id: Int?) {
        viewModelScope.launch {
            if (id == null || id == 0) {
                _settings.value = SmtpSettings(id = 0, accountName = "")
            } else {
                _settings.value = repo.getById(id) ?: SmtpSettings(id = 0, accountName = "")
            }
            _testResult.value = null
            _saveResult.value = null
        }
    }

    fun updateSettings(s: SmtpSettings) {
        val oldUsername = _settings.value.username
        var updated = s
        if (s.username != oldUsername && s.username.contains("@")) {
            val domain = s.username.substringAfter("@").lowercase().trim()
            if (domain.startsWith("gmx.")) {
                updated = s.copy(host = "mail.gmx.net", port = 587, security = SecurityType.STARTTLS)
            } else if (domain.startsWith("web.de")) {
                updated = s.copy(host = "smtp.web.de", port = 587, security = SecurityType.STARTTLS)
            } else if (domain.startsWith("gmail.")) {
                updated = s.copy(host = "smtp.gmail.com", port = 587, security = SecurityType.STARTTLS)
            } else if (domain.startsWith("outlook.") || domain.startsWith("hotmail.") || domain.startsWith("live.")) {
                updated = s.copy(host = "smtp-mail.outlook.com", port = 587, security = SecurityType.STARTTLS)
            }
        }
        _settings.value = updated
    }

    fun save() {
        viewModelScope.launch {
            val toSave = if (_settings.value.accountName.isBlank()) {
                _settings.value.copy(accountName = _settings.value.username.ifBlank { "Konto" })
            } else {
                _settings.value
            }
            repo.save(toSave)
            _saveResult.value = "Konto gespeichert"
        }
    }

    fun deleteAccount(id: Int) {
        viewModelScope.launch {
            repo.delete(id)
        }
    }

    fun testConnection() {
        val current = _settings.value
        viewModelScope.launch {
            _isTesting.value = true
            _testResult.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val props = Properties().apply {
                        put("mail.smtp.host", current.host)
                        put("mail.smtp.port", current.port.toString())
                        put("mail.smtp.auth", "true")
                        put("mail.smtp.connectiontimeout", "8000")
                        put("mail.smtp.timeout", "8000")
                        when (current.security) {
                            SecurityType.STARTTLS -> {
                                put("mail.smtp.starttls.enable", "true")
                                put("mail.smtp.starttls.required", "true")
                            }
                            SecurityType.SSL_TLS -> {
                                put("mail.smtp.socketFactory.port", current.port.toString())
                                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                                put("mail.smtp.socketFactory.fallback", "false")
                            }
                            SecurityType.NONE -> {
                                // No secure configuration
                            }
                        }
                    }

                    val session = Session.getInstance(props, object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication {
                            return PasswordAuthentication(current.username, current.password)
                        }
                    })

                    val transport = session.transport
                    transport.connect()
                    transport.close()
                    "Verbindung erfolgreich!"
                }
                _testResult.value = result
            } catch (e: Exception) {
                _testResult.value = "Fehler: ${e.localizedMessage ?: e.message ?: "Unbekannter Fehler"}"
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun clearTestResult() {
        _testResult.value = null
    }
}
