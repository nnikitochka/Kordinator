package org.airsonic.player.controller.rest.login

import org.slf4j.LoggerFactory

/** Keeps the legacy access denied page's request logging while the servlet adapter renders its JSP. */
class AccessDeniedPageController {
    fun logAccessDenied(remoteAddress: String, requestUrl: String) {
        logger.info("The IP {} tried to access the forbidden url {}.", remoteAddress, requestUrl)
    }

    companion object {
        @JvmField val PATHS = arrayOf("/accessDenied", "/accessDenied.view")
        private val logger = LoggerFactory.getLogger(AccessDeniedPageController::class.java)
    }
}
