package com.example.emailbot.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.emailbot.data.EmailTaskRepository
import com.example.emailbot.data.SmtpSettings
import com.example.emailbot.data.SmtpSettingsRepository
import com.example.emailbot.mail.MailProviders
import com.example.emailbot.mail.SmtpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data object Success : TestState
    data class Failed(val message: String) : TestState
}

data class AccountWithUsage(val account: SmtpSettings, val taskCount: Int, val isDefault: Boolean)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SmtpSettingsRepository(application)
    private val taskRepo = EmailTaskRepository(application)

    /** Null while loading. */
    val accounts: StateFlow<List<AccountWithUsage>?> =
        combine(repo.accounts, taskRepo.tasks) { accounts, tasks ->
            val defaultId = accounts.firstOrNull()?.id
            accounts.map { acc ->
                AccountWithUsage(
                    account = acc,
                    taskCount = tasks.count { it.smtpAccountId == acc.id || (it.smtpAccountId == 0 && acc.id == defaultId) },
                    isDefault = acc.id == defaultId
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun deleteAccount(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val moved = taskRepo.reassignAccount(id)
            repo.delete(id)
            _messages.send(
                if (moved == 0) "Konto gelöscht"
                else "Konto gelöscht · $moved E-Mail(s) nutzen jetzt das Standardkonto"
            )
        }
    }

    // ── Editor state ──

    private val _form = MutableStateFlow(SmtpSettings())
    val form: StateFlow<SmtpSettings> = _form.asStateFlow()

    private val _portText = MutableStateFlow("587")
    val portText: StateFlow<String> = _portText.asStateFlow()

    private val _test = MutableStateFlow<TestState>(TestState.Idle)
    val test: StateFlow<TestState> = _test.asStateFlow()

    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    private var loadedId: Int? = -1

    fun load(id: Int?) {
        if (loadedId == id) return // keep edits across recomposition / rotation
        loadedId = id
        viewModelScope.launch {
            val account = id?.let { repo.getById(it) } ?: SmtpSettings()
            _form.value = account
            _portText.value = account.port.toString()
            _test.value = TestState.Idle
        }
    }

    fun update(transform: (SmtpSettings) -> SmtpSettings) {
        val old = _form.value
        var new = transform(old)
        // Typing a known address fills in the server settings, as long as the user hasn't set a custom server.
        if (new.username != old.username) {
            val provider = MailProviders.forAddress(new.username)
            val hostIsDefault = old.host.isBlank() || MailProviders.forHost(old.host) != null
            if (provider != null && hostIsDefault) {
                new = new.copy(host = provider.host, port = provider.port, security = provider.security)
                _portText.value = provider.port.toString()
            }
        }
        _form.value = new
        _test.value = TestState.Idle
    }

    fun updatePort(text: String) {
        val digits = text.filter { it.isDigit() }.take(5)
        _portText.value = digits
        digits.toIntOrNull()?.let { port -> _form.value = _form.value.copy(port = port) }
        _test.value = TestState.Idle
    }

    /** Field name → error, empty if the form is valid. */
    fun validate(): Map<String, String> = buildMap {
        val f = _form.value
        if (f.username.isBlank()) put("username", "Pflichtfeld")
        if (f.password.isBlank()) put("password", "Pflichtfeld")
        if (f.host.isBlank()) put("host", "Pflichtfeld")
        val port = _portText.value.toIntOrNull()
        if (port == null || port !in 1..65535) put("port", "1–65535")
    }

    fun testConnection() {
        if (validate().isNotEmpty()) return
        val current = _form.value
        _test.value = TestState.Running
        viewModelScope.launch {
            _test.value = withContext(Dispatchers.IO) {
                runCatching { SmtpClient.testConnection(current) }
                    .fold({ TestState.Success }, { TestState.Failed(SmtpClient.describe(it)) })
            }
        }
    }

    fun save(): Boolean {
        if (validate().isNotEmpty()) return false
        val f = _form.value
        viewModelScope.launch(Dispatchers.IO) {
            repo.save(f.copy(accountName = f.accountName.trim(), host = f.host.trim(), username = f.username.trim()))
            _saved.send(Unit)
        }
        return true
    }
}
