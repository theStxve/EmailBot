package com.example.emailbot.mail

import com.example.emailbot.data.EmailTask
import com.example.emailbot.data.SecurityType
import com.example.emailbot.data.SmtpSettings
import java.io.File
import java.util.Date
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.AuthenticationFailedException
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/** Thrown for problems that won't go away by retrying (bad address, missing attachment …). */
class PermanentSendException(message: String) : Exception(message)

/**
 * The one place that talks SMTP. The connection test and the real send use the same
 * session setup, so a successful test means the send will connect the same way.
 */
object SmtpClient {

    private const val TIMEOUT_MS = 15_000

    private fun session(smtp: SmtpSettings): Session {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", smtp.host.trim())
            put("mail.smtp.port", smtp.port.toString())
            put("mail.smtp.connectiontimeout", TIMEOUT_MS.toString())
            put("mail.smtp.timeout", TIMEOUT_MS.toString())
            put("mail.smtp.writetimeout", TIMEOUT_MS.toString())
            when (smtp.security) {
                SecurityType.STARTTLS -> {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                    put("mail.smtp.ssl.checkserveridentity", "true")
                }
                SecurityType.SSL_TLS -> {
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.ssl.checkserveridentity", "true")
                }
                SecurityType.NONE -> Unit
            }
        }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(smtp.username.trim(), smtp.password)
        })
    }

    /** Connects and authenticates without sending anything. Throws on failure. */
    fun testConnection(smtp: SmtpSettings) {
        session(smtp).transport.use { it.connect() }
    }

    /** Throws [PermanentSendException] for errors a retry can't fix, [MessagingException] otherwise. */
    fun send(task: EmailTask, smtp: SmtpSettings) {
        if (smtp.username.isBlank() || smtp.host.isBlank()) {
            throw PermanentSendException("Das SMTP-Konto ist unvollständig eingerichtet.")
        }

        val attachment = task.attachmentPath.takeIf { it.isNotBlank() }?.let(::File)
        if (attachment != null && !attachment.exists()) {
            throw PermanentSendException("Anhang „${task.attachmentName}“ wurde nicht gefunden. Bitte neu anhängen.")
        }

        val session = session(smtp)
        val message = MimeMessage(session).apply {
            setFrom(
                if (smtp.fromName.isNotBlank()) InternetAddress(smtp.username.trim(), smtp.fromName, "UTF-8")
                else InternetAddress(smtp.username.trim())
            )
            setSubject(task.subject, "UTF-8")
            setRecipients(Message.RecipientType.TO, parseAddresses(task.recipientTo, "An"))
            if (task.recipientCc.isNotBlank()) setRecipients(Message.RecipientType.CC, parseAddresses(task.recipientCc, "CC"))
            if (task.recipientBcc.isNotBlank()) setRecipients(Message.RecipientType.BCC, parseAddresses(task.recipientBcc, "BCC"))
            sentDate = Date()

            if (attachment != null) {
                setContent(MimeMultipart().apply {
                    addBodyPart(MimeBodyPart().apply { setText(task.body, "UTF-8") })
                    addBodyPart(MimeBodyPart().apply {
                        dataHandler = DataHandler(FileDataSource(attachment))
                        fileName = task.attachmentName.ifBlank { attachment.name }
                    })
                })
            } else {
                setText(task.body, "UTF-8")
            }
        }

        try {
            Transport.send(message)
        } catch (e: AuthenticationFailedException) {
            throw PermanentSendException("Anmeldung fehlgeschlagen. Bitte Benutzername und Passwort prüfen.")
        }
    }

    private fun parseAddresses(raw: String, field: String): Array<InternetAddress> =
        try {
            InternetAddress.parse(raw.replace(';', ','), true)
        } catch (e: AddressException) {
            throw PermanentSendException("Ungültige Adresse im Feld $field: ${e.ref ?: raw}")
        }

    /** True if [raw] is a non-empty, comma or semicolon separated list of valid addresses. */
    fun isValidAddressList(raw: String): Boolean =
        raw.isNotBlank() && runCatching {
            val list = InternetAddress.parse(raw.replace(';', ','), true)
            // JavaMail accepts bare local names like "anna"; we require user@domain.
            list.isNotEmpty() && list.all { addr ->
                addr.validate()
                val domain = addr.address.substringAfter('@', "")
                domain.isNotEmpty() && '.' in domain && !domain.endsWith('.')
            }
        }.getOrDefault(false)

    /** Turns JavaMail exceptions into something a user can act on. */
    fun describe(e: Throwable): String = when {
        e is PermanentSendException -> e.message.orEmpty()
        e is AuthenticationFailedException -> "Anmeldung fehlgeschlagen. Bitte Benutzername und Passwort prüfen."
        e.causeChain().any { it is java.net.UnknownHostException } -> "Server nicht gefunden. Bitte SMTP-Server und Internetverbindung prüfen."
        e.causeChain().any { it is java.net.SocketTimeoutException || it is java.net.ConnectException } ->
            "Keine Verbindung zum Server. Bitte Port und Verschlüsselung prüfen."
        e.causeChain().any { it is javax.net.ssl.SSLException } -> "Verschlüsselte Verbindung fehlgeschlagen. Bitte Port und Verschlüsselung prüfen."
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause.takeIf { c -> c !== it } }
}
