package com.example.emailbot.mail

import com.example.emailbot.data.SecurityType

data class MailProvider(
    val name: String,
    val domains: List<String>,
    val host: String,
    val port: Int,
    val security: SecurityType,
    /** What the user has to do in the provider's web settings first. */
    val hint: String
)

object MailProviders {

    val all = listOf(
        MailProvider(
            "Gmail", listOf("gmail.com", "googlemail.com"), "smtp.gmail.com", 587, SecurityType.STARTTLS,
            "Gmail braucht ein App-Passwort: Google-Konto → Sicherheit → 2-Faktor-Authentifizierung aktivieren → App-Passwörter."
        ),
        MailProvider(
            "Outlook", listOf("outlook.com", "outlook.de", "hotmail.com", "hotmail.de", "live.com", "live.de", "msn.com"),
            "smtp-mail.outlook.com", 587, SecurityType.STARTTLS,
            "Outlook braucht ein App-Passwort: Microsoft-Konto → Sicherheit → Erweiterte Sicherheitsoptionen → App-Kennwörter."
        ),
        MailProvider(
            "GMX", listOf("gmx.de", "gmx.net", "gmx.at", "gmx.ch", "gmx.com"), "mail.gmx.net", 587, SecurityType.STARTTLS,
            "Bei GMX im Browser unter E-Mail → Einstellungen → POP3 & IMAP den Zugriff per „POP3/IMAP und SMTP“ erlauben."
        ),
        MailProvider(
            "WEB.DE", listOf("web.de"), "smtp.web.de", 587, SecurityType.STARTTLS,
            "Bei WEB.DE im Browser unter E-Mail → Einstellungen → POP3 & IMAP den Zugriff per „POP3/IMAP und SMTP“ erlauben."
        ),
        MailProvider(
            "iCloud", listOf("icloud.com", "me.com", "mac.com"), "smtp.mail.me.com", 587, SecurityType.STARTTLS,
            "iCloud braucht ein app-spezifisches Passwort: appleid.apple.com → Anmeldung und Sicherheit."
        ),
        MailProvider(
            "Yahoo", listOf("yahoo.com", "yahoo.de"), "smtp.mail.yahoo.com", 465, SecurityType.SSL_TLS,
            "Yahoo braucht ein App-Passwort: Kontosicherheit → App-Passwort generieren."
        ),
    )

    fun forAddress(address: String): MailProvider? {
        val domain = address.substringAfter('@', "").trim().lowercase()
        if (domain.isEmpty()) return null
        return all.find { domain in it.domains }
    }

    fun forHost(host: String): MailProvider? = all.find { it.host.equals(host.trim(), ignoreCase = true) }
}
