package su.nnedition.kord.security

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import su.nnedition.kord.service.SettingsService
import org.slf4j.LoggerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Properties

fun interface PasswordRecoveryEmailSender {
    fun send(recipient: String, subject: String, body: String)
}

/** Sends one-time password reset links for standalone Kordinator accounts. */
class StandalonePasswordRecoveryService(
    private val auth: LocalAuthService,
    private val emailSender: PasswordRecoveryEmailSender,
) : AutoCloseable {
    private val emailScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun request(email: String) {
        val delivery = try {
            auth.createPasswordRecovery(email)
        } catch (_: IllegalArgumentException) {
            null
        } ?: return
        emailScope.launch {
            try {
                emailSender.send(
                    delivery.email,
                    "Kordinator password reset",
                    "A password reset was requested for ${delivery.username}.\n\n" +
                        "Use this one-time token with POST /api/v1/auth/password-recovery/reset:\n" +
                        delivery.token + "\n\n" +
                        "The token expires in 30 minutes. If you did not request this, ignore this message.",
                )
            } catch (_: Exception) {
                LOG.warn("Could not send password recovery email")
            }
        }
    }

    fun reset(token: String, newPassword: String): Boolean = auth.resetPasswordWithRecoveryToken(token, newPassword)

    override fun close() {
        emailScope.cancel()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(StandalonePasswordRecoveryService::class.java)

        fun smtp(settings: SettingsService): PasswordRecoveryEmailSender = PasswordRecoveryEmailSender { recipient, subject, body ->
            val server = settings.getSmtpServer()
            require(!server.isNullOrBlank()) { "SMTP server is not configured" }
            val protocol = if (settings.getSmtpEncryption() == "SSL/TLS") "smtps" else "smtp"
            val properties = Properties().apply {
                setProperty("mail.$protocol.host", server)
                setProperty("mail.$protocol.port", settings.getSmtpPort())
                if (settings.getSmtpEncryption() == "SSL/TLS") setProperty("mail.smtps.ssl.enable", "true")
                if (settings.getSmtpEncryption() == "STARTTLS") setProperty("mail.smtp.starttls.enable", "true")
                if (!settings.getSmtpUser().isNullOrBlank()) setProperty("mail.$protocol.auth", "true")
            }
            val session = Session.getInstance(properties, null)
            val message: Message = MimeMessage(session).apply {
                setFrom(InternetAddress(settings.getSmtpFrom()))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
                setSubject(subject)
                setText(body)
                sentDate = Date()
            }
            session.getTransport(protocol).use { transport ->
                if (!settings.getSmtpUser().isNullOrBlank()) {
                    transport.connect(server, settings.getSmtpUser(), settings.getSmtpPassword())
                } else {
                    transport.connect()
                }
                transport.sendMessage(message, message.allRecipients)
            }
        }
    }

}
