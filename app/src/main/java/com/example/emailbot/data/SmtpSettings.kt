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
private val accountListSerializer = ListSerializer(SmtpSettings.serializer())

@Serializable
data class SmtpSettings(
    val id: Int            = 0,
    val accountName: String = "",
    val host: String       = "",
    val port: Int          = 587,
    val username: String   = "",
    /** Plain text in memory; stored encrypted (see [SecretCipher]). */
    val password: String   = "",
    val security: SecurityType = SecurityType.STARTTLS,
    val fromName: String   = ""
)

val SmtpSettings.displayName: String
    get() = accountName.ifBlank { username }

class SmtpSettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.smtpDataStore

    private fun decode(raw: String?): List<SmtpSettings> =
        raw?.let { runCatching { json.decodeFromString(accountListSerializer, it) }.getOrNull() } ?: emptyList()

    private fun encode(list: List<SmtpSettings>): String =
        json.encodeToString(accountListSerializer, list.map { it.copy(password = SecretCipher.encrypt(it.password)) })

    /** Accounts with decrypted passwords. The first account is the default. */
    val accounts: Flow<List<SmtpSettings>> = dataStore.data.map { prefs ->
        decode(prefs[ACCOUNTS_KEY]).map { it.copy(password = SecretCipher.decrypt(it.password)) }
    }

    suspend fun getAll(): List<SmtpSettings> = accounts.first()

    suspend fun getById(id: Int): SmtpSettings? = getAll().find { it.id == id }

    /** Resolves the account a task should use: 0 means the default account. */
    suspend fun resolve(accountId: Int): SmtpSettings? =
        if (accountId == 0) getAll().firstOrNull() else getById(accountId)

    suspend fun save(settings: SmtpSettings): SmtpSettings {
        var saved = settings
        dataStore.edit { prefs ->
            val current = decode(prefs[ACCOUNTS_KEY]).map { it.copy(password = SecretCipher.decrypt(it.password)) }
            val newList = if (settings.id == 0) {
                saved = settings.copy(id = (current.maxOfOrNull { it.id } ?: 0) + 1)
                current + saved
            } else {
                current.map { if (it.id == settings.id) settings else it }
            }
            prefs[ACCOUNTS_KEY] = encode(newList)
        }
        return saved
    }

    suspend fun delete(id: Int) {
        dataStore.edit { prefs ->
            val current = decode(prefs[ACCOUNTS_KEY]).map { it.copy(password = SecretCipher.decrypt(it.password)) }
            prefs[ACCOUNTS_KEY] = encode(current.filter { it.id != id })
        }
    }

    /** Re-encrypts passwords that are still stored in plain text (from older app versions). */
    suspend fun encryptLegacyPasswords() {
        dataStore.edit { prefs ->
            val raw = decode(prefs[ACCOUNTS_KEY])
            if (raw.any { it.password.isNotEmpty() && !SecretCipher.isEncrypted(it.password) }) {
                prefs[ACCOUNTS_KEY] = encode(raw.map { it.copy(password = SecretCipher.decrypt(it.password)) })
            }
        }
    }
}
