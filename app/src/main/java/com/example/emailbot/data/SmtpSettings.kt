package com.example.emailbot.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.smtpDataStore by preferencesDataStore(name = "smtp_accounts")
private val ACCOUNTS_KEY = stringPreferencesKey("accounts_json")

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class SmtpSettings(
    val id: Int            = 0,
    val accountName: String = "",
    val host: String       = "smtp.gmail.com",
    val port: Int          = 587,
    val username: String   = "",
    val password: String   = "",
    val security: SecurityType = SecurityType.STARTTLS,
    val fromName: String   = ""
)

class SmtpSettingsRepository(private val context: Context) {

    val accounts: Flow<List<SmtpSettings>> = context.smtpDataStore.data.map { prefs ->
        val raw = prefs[ACCOUNTS_KEY] ?: return@map emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(SmtpSettings.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun getAll(): List<SmtpSettings> = accounts.first()

    suspend fun getById(id: Int): SmtpSettings? = getAll().find { it.id == id }

    suspend fun getDefault(): SmtpSettings = getAll().firstOrNull() ?: SmtpSettings()

    suspend fun save(settings: SmtpSettings): SmtpSettings {
        var saved = settings
        context.smtpDataStore.edit { prefs ->
            val current = prefs[ACCOUNTS_KEY]
                ?.let { runCatching { json.decodeFromString(ListSerializer(SmtpSettings.serializer()), it) }.getOrDefault(emptyList()) }
                ?: emptyList()

            val newList = if (settings.id == 0) {
                val nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
                saved = settings.copy(id = nextId)
                current + saved
            } else {
                current.map { if (it.id == settings.id) settings else it }
            }
            prefs[ACCOUNTS_KEY] = json.encodeToString(ListSerializer(SmtpSettings.serializer()), newList)
        }
        return saved
    }

    suspend fun delete(id: Int) {
        context.smtpDataStore.edit { prefs ->
            val current = prefs[ACCOUNTS_KEY]
                ?.let { runCatching { json.decodeFromString(ListSerializer(SmtpSettings.serializer()), it) }.getOrDefault(emptyList()) }
                ?: emptyList()
            prefs[ACCOUNTS_KEY] = json.encodeToString(ListSerializer(SmtpSettings.serializer()), current.filter { it.id != id })
        }
    }

    // Legacy shim: load the old SharedPreferences account and migrate it once
    fun migrateLegacyIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("smtp_settings", Context.MODE_PRIVATE)
        val host = prefs.getString("host", null) ?: return
        val username = prefs.getString("username", "") ?: ""
        if (username.isBlank()) return
        val legacy = SmtpSettings(
            id          = 0,
            accountName = username,
            host        = host,
            port        = prefs.getInt("port", 587),
            username    = username,
            password    = prefs.getString("password", "") ?: "",
            security    = SecurityType.valueOf(prefs.getString("security", SecurityType.STARTTLS.name) ?: SecurityType.STARTTLS.name),
            fromName    = prefs.getString("from_name", "") ?: ""
        )
        prefs.edit().clear().apply()
        kotlinx.coroutines.runBlocking { save(legacy) }
    }
}
