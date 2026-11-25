package su.nnedition.kord.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import su.nnedition.kord.api.configureApiRouting
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorStandaloneModuleTest {
    @Test
    fun healthIsJsonAndDoesNotRequireWebsiteInfrastructure() = testApplication {
        application { configureApiRouting() }

        val response = client.get("/api/v1/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("application/json", response.headers["Content-Type"]?.substringBefore(';'))
        assertEquals("{\"status\":\"UP\"}", response.bodyAsText())
    }
}
