package su.nnedition.kord.security

import com.google.common.io.BaseEncoding
import su.nnedition.kord.service.JWTSecurityService
import su.nnedition.kord.service.SettingsService
import su.nnedition.kord.security.crypto.PasswordEncoder
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.OpenBSDBCrypt
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.digests.MD4Digest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Spring-independent implementations for password formats already stored by Kordinator. */
object LegacyPasswordEncoders {
    private val random = SecureRandom()
    private val hex = BaseEncoding.base16().lowerCase()
    private val base64 = Base64.getEncoder().withoutPadding()
    private val base64Decoder = Base64.getDecoder()

    @JvmField
    val CODECS: MutableMap<String, PasswordEncoder> = create().toMutableMap()

    @JvmField
    val OPENTEXT_ENCODERS: Set<String> = setOf("noop", "hex", "legacynoop", "legacyhex")

    @JvmField
    val DECODABLE_ENCODERS: Set<String> = OPENTEXT_ENCODERS + "encrypted-AES-GCM"

    @JvmField
    val NONLEGACY_ENCODERS: Set<String> = CODECS.keys.filterNot {
        it.contains("legacy") || it.contains("salttoken")
    }.toSet()

    @JvmField
    val NONLEGACY_DECODABLE_ENCODERS: Set<String> = DECODABLE_ENCODERS intersect NONLEGACY_ENCODERS

    @JvmField
    val NONLEGACY_NONDECODABLE_ENCODERS: Set<String> = NONLEGACY_ENCODERS - DECODABLE_ENCODERS

    @JvmStatic
    fun create(): Map<String, PasswordEncoder> = linkedMapOf(
        "bcrypt" to BcryptEncoder(),
        "ldap" to LdapShaEncoder(),
        "MD4" to MessageDigestEncoder("MD4"),
        "MD5" to MessageDigestEncoder("MD5"),
        "pbkdf2" to Pbkdf2Encoder(),
        "scrypt" to ScryptEncoder(),
        "SHA-1" to MessageDigestEncoder("SHA-1"),
        "SHA-256" to MessageDigestEncoder("SHA-256"),
        "sha256" to StandardSha256Encoder(),
        "argon2" to Argon2Encoder(),
        "noop" to NoopEncoder,
        "hex" to HexPasswordEncoder.instance,
        "encrypted-AES-GCM" to AesGcmPasswordEncoder(),
        "noopsalttoken" to SaltedTokenPasswordEncoder(NoopEncoder),
        "hexsalttoken" to SaltedTokenPasswordEncoder(HexPasswordEncoder.instance),
        "encrypted-AES-GCMsalttoken" to SaltedTokenPasswordEncoder(AesGcmPasswordEncoder()),
        "legacynoop" to NoopEncoder,
        "legacyhex" to HexPasswordEncoder.instance,
        "legacynoopsalttoken" to SaltedTokenPasswordEncoder(NoopEncoder),
        "legacyhexsalttoken" to SaltedTokenPasswordEncoder(HexPasswordEncoder.instance),
    )

    @JvmStatic
    fun configureEncryption(settingsService: SettingsService) {
        var generatedKeys = false
        var keyPass = settingsService.getEncryptionPassword()
        if (keyPass.isNullOrBlank()) {
            keyPass = JWTSecurityService.generateKey()
            settingsService.setEncryptionPassword(keyPass)
            generatedKeys = true
        }
        var keySalt = settingsService.getEncryptionSalt()
        if (keySalt.isNullOrBlank()) {
            val bytes = ByteArray(16).also(random::nextBytes)
            keySalt = BaseEncoding.base16().encode(bytes)
            settingsService.setEncryptionSalt(keySalt)
            generatedKeys = true
        }
        if (generatedKeys) settingsService.save()
        val aes = AesGcmPasswordEncoder(keyPass, keySalt)
        CODECS["encrypted-AES-GCM"] = aes
        CODECS["encrypted-AES-GCMsalttoken"] = SaltedTokenPasswordEncoder(aes)
    }

    private object NoopEncoder : PasswordEncoder, PasswordDecoder {
        override fun encode(rawPassword: CharSequence): String = rawPassword.toString()
        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean =
            MessageDigest.isEqual(utf8(rawPassword.toString()), utf8(encodedPassword))
        override fun decode(encoded: String): String = encoded
    }

    private class BcryptEncoder : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String =
            OpenBSDBCrypt.generate("2a", rawPassword.toString().toCharArray(), randomBytes(16), 10)

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean =
            runCatching { OpenBSDBCrypt.checkPassword(encodedPassword, rawPassword.toString().toCharArray()) }
                .getOrDefault(false)

        override fun upgradeEncoding(encodedPassword: String): Boolean =
            runCatching { encodedPassword.substring(4, 6).toInt() < 10 }.getOrDefault(false)
    }

    private class LdapShaEncoder : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String {
            val salt = randomBytes(8)
            val sha = MessageDigest.getInstance("SHA-1")
            sha.update(utf8(rawPassword.toString()))
            val digest = sha.digest(salt)
            return "{SSHA}" + Base64.getEncoder().encodeToString(digest + salt)
        }

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean = runCatching {
            val prefixEnd = encodedPassword.indexOf('}')
            if (prefixEnd < 0) return@runCatching MessageDigest.isEqual(utf8(rawPassword.toString()), utf8(encodedPassword))
            val prefix = encodedPassword.substring(0, prefixEnd + 1).uppercase()
            val payload = base64Decoder.decode(encodedPassword.substring(prefixEnd + 1))
            val salted = prefix == "{SSHA}"
            if (!salted && prefix != "{SHA}") return@runCatching false
            val salt = if (salted) payload.copyOfRange(20, payload.size) else byteArrayOf()
            val hash = MessageDigest.getInstance("SHA-1").digest(utf8(rawPassword.toString()) + salt)
            MessageDigest.isEqual(if (salted) hash + salt else hash, payload)
        }.getOrDefault(false)
    }

    private class MessageDigestEncoder(private val algorithm: String) : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String {
            val salt = "{" + Base64.getEncoder().encodeToString(randomBytes(32)) + "}"
            return salt + digest(algorithm, utf8(rawPassword.toString() + salt)).let(hex::encode)
        }

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean = runCatching {
            val salt = if (encodedPassword.startsWith("{")) {
                encodedPassword.substringBefore('}') + "}"
            } else ""
            val actual = salt + digest(algorithm, utf8(rawPassword.toString() + salt)).let(hex::encode)
            constantTimeEquals(actual, encodedPassword)
        }.getOrDefault(false)
    }

    private class Pbkdf2Encoder : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String {
            val salt = randomBytes(8)
            return hex.encode(salt + derivePbkdf2(rawPassword, salt))
        }

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean = runCatching {
            val all = hex.decode(encodedPassword)
            if (all.size != 40) return@runCatching false
            val salt = all.copyOfRange(0, 8)
            MessageDigest.isEqual(all, salt + derivePbkdf2(rawPassword, salt))
        }.getOrDefault(false)

        private fun derivePbkdf2(password: CharSequence, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(password.toString().toCharArray(), salt, 185000, 256)
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }
    }

    private class ScryptEncoder : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String {
            val salt = randomBytes(64)
            val derived = SCrypt.generate(utf8(rawPassword.toString()), salt, 16384, 8, 1, 32)
            return "$" + "e0801" + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(derived)
        }

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean = runCatching {
            val parts = encodedPassword.split('$')
            if (parts.size != 4) return@runCatching false
            val params = parts[1].toLong(16)
            val n = 1 shl ((params shr 16) and 0xffff).toInt()
            val r = ((params shr 8) and 0xff).toInt()
            val p = (params and 0xff).toInt()
            val salt = Base64.getDecoder().decode(parts[2])
            val stored = Base64.getDecoder().decode(parts[3])
            val actual = SCrypt.generate(utf8(rawPassword.toString()), salt, n, r, p, stored.size)
            MessageDigest.isEqual(stored, actual)
        }.getOrDefault(false)

        override fun upgradeEncoding(encodedPassword: String): Boolean = runCatching {
            val params = encodedPassword.split('$')[1].toLong(16)
            (1 shl ((params shr 16) and 0xffff).toInt()) < 16384 ||
                ((params shr 8) and 0xff).toInt() < 8 || (params and 0xff).toInt() < 1
        }.getOrDefault(false)
    }

    private class StandardSha256Encoder : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String {
            val salt = randomBytes(8)
            return hex.encode(salt + standardDigest(rawPassword, salt))
        }

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean = runCatching {
            val all = hex.decode(encodedPassword)
            if (all.size != 40) return@runCatching false
            val salt = all.copyOfRange(0, 8)
            MessageDigest.isEqual(all, salt + standardDigest(rawPassword, salt))
        }.getOrDefault(false)

        private fun standardDigest(password: CharSequence, salt: ByteArray): ByteArray {
            var value = salt + utf8(password.toString())
            repeat(1024) { value = digest("SHA-256", value) }
            return value
        }
    }

    private class Argon2Encoder : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String {
            val salt = randomBytes(16)
            val hash = deriveArgon2(rawPassword, salt, 32, 4096, 1, 1)
            return "\$argon2id\$v=19\$m=4096,t=1,p=1\$${base64.encodeToString(salt)}\$${base64.encodeToString(hash)}"
        }

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean = runCatching {
            val parts = encodedPassword.split('$')
            if (parts.size != 6 || parts[1] != "argon2id") return@runCatching false
            val version = parts[2].removePrefix("v=").toInt()
            val params = parts[3].split(',').associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
            val salt = base64Decoder.decode(parts[4])
            val expected = base64Decoder.decode(parts[5])
            val actual = deriveArgon2(rawPassword, salt, expected.size, params.getValue("m"), params.getValue("t"), params.getValue("p"), version)
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)

        private fun deriveArgon2(
            password: CharSequence,
            salt: ByteArray,
            length: Int,
            memory: Int,
            iterations: Int,
            parallelism: Int,
            version: Int = Argon2Parameters.ARGON2_VERSION_13,
        ): ByteArray {
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(version).withSalt(salt).withParallelism(parallelism)
                .withMemoryAsKB(memory).withIterations(iterations).build()
            return ByteArray(length).also { out ->
                Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password.toString().toCharArray(), out)
                parameters.clear()
            }
        }
    }

    private fun digest(algorithm: String, value: ByteArray): ByteArray = when (algorithm) {
        "MD4" -> ByteArray(16).also { MD4Digest().apply { update(value, 0, value.size); doFinal(it, 0) } }
        else -> MessageDigest.getInstance(algorithm).digest(value)
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
    private fun utf8(value: String): ByteArray = value.toByteArray(StandardCharsets.UTF_8)
    private fun constantTimeEquals(first: String, second: String): Boolean =
        MessageDigest.isEqual(utf8(first), utf8(second))
}
