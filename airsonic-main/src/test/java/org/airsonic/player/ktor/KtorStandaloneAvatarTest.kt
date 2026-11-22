package org.airsonic.player.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.airsonic.player.runtime.StandaloneDatabase
import org.airsonic.player.security.LocalAuthService
import org.airsonic.player.service.StandaloneAvatarService
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorStandaloneAvatarTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun avatarUploadIsNormalizedPrivateAndRemovable() {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:hsqldb:mem:standalone_avatar_${home.fileName}"
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        })

        dataSource.use {
            StandaloneDatabase.migrate(dataSource)
            val auth = LocalAuthService(dataSource)
            auth.createInitialAdmin("admin", "a strong password for tests")
            val userId = auth.createUser("listener", "listener password for tests")
            val userToken = auth.login("listener", "listener password for tests")!!.token
            val otherToken = auth.login("admin", "a strong password for tests")!!.token
            val avatarService = StandaloneAvatarService(home.resolve("avatars"))
            val sourceImage = BufferedImage(128, 32, BufferedImage.TYPE_INT_RGB)
            val sourceBytes = ByteArrayOutputStream().use { output ->
                ImageIO.write(sourceImage, "png", output)
                output.toByteArray()
            }
            val fileHeaders = Headers.build {
                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"profile.png\"")
                append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
            }

            testApplication {
                application { standaloneModule(authService = auth, avatarService = avatarService) }

                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/me/avatar").status)
                assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/me/avatar") { bearerAuth(userToken) }.status)
                val uploaded = client.put("/api/v1/me/avatar") {
                    bearerAuth(userToken)
                    setBody(MultiPartFormDataContent(formData { append("file", sourceBytes, fileHeaders) }))
                }
                assertEquals(HttpStatusCode.Created, uploaded.status, uploaded.bodyAsText())
                assertTrue(uploaded.bodyAsText().contains("\"width\":64"))
                assertTrue(uploaded.bodyAsText().contains("\"height\":16"))
                val avatar = client.get("/api/v1/me/avatar") { bearerAuth(userToken) }
                assertEquals(HttpStatusCode.OK, avatar.status)
                assertEquals(ContentType.Image.PNG.toString(), avatar.headers[HttpHeaders.ContentType])
                val bytes = avatar.bodyAsBytes()
                assertTrue(!bytes.contentEquals(sourceBytes))
                assertEquals(64, avatarService.get(userId)?.width)
                assertEquals(16, avatarService.get(userId)?.height)
                assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/me/avatar") { bearerAuth(otherToken) }.status)

                val invalid = client.put("/api/v1/me/avatar") {
                    bearerAuth(userToken)
                    setBody(MultiPartFormDataContent(formData { append("file", "not an image".toByteArray(), fileHeaders) }))
                }
                assertEquals(HttpStatusCode.BadRequest, invalid.status)
                assertEquals(HttpStatusCode.NoContent, client.delete("/api/v1/me/avatar") { bearerAuth(userToken) }.status)
                assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/me/avatar") { bearerAuth(userToken) }.status)
            }
        }
    }
}
