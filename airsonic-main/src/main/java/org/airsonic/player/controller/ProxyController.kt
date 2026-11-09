package org.airsonic.player.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.airsonic.player.util.FileUtil
import org.apache.commons.io.IOUtils
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.ServletRequestUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.ModelAndView
import java.io.InputStream

/**
 * A proxy for external HTTP requests.
 */
@Controller
@RequestMapping("/proxy")
class ProxyController {
    companion object {
        private val LOG = LoggerFactory.getLogger(ProxyController::class.java)
    }

    @GetMapping
    @Throws(Exception::class)
    fun handleRequest(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
        // Count legacy clients without exposing target URLs, credentials or tokens.
        LOG.info("Legacy /proxy endpoint invoked; check usage before removing this route")
        val url = ServletRequestUtils.getRequiredStringParameter(request, "url")

        val requestConfig = RequestConfig.custom()
            .setConnectTimeout(15000)
            .setSocketTimeout(15000)
            .build()
        val method = HttpGet(url)
        method.config = requestConfig

        var input: InputStream? = null
        try {
            HttpClients.createDefault().use { client ->
                client.execute(method).use { resp ->
                    val statusCode = resp.statusLine.statusCode
                    if (statusCode != HttpStatus.OK.value()) {
                        response.sendError(statusCode)
                    } else {
                        input = resp.entity.content
                        IOUtils.copy(input, response.outputStream)
                    }
                }
            }
        } finally {
            FileUtil.closeQuietly(input)
        }
        return null
    }
}
