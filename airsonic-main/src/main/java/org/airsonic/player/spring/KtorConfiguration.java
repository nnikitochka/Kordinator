package org.airsonic.player.spring;

import io.ktor.server.servlet.jakarta.ServletApplicationEngine;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Connects Ktor to the existing servlet container during the migration. */
@Configuration
public class KtorConfiguration {

    @Bean
    public ServletRegistrationBean<ServletApplicationEngine> ktorServletRegistration() {
        ServletRegistrationBean<ServletApplicationEngine> registration =
                new ServletRegistrationBean<>(new AirsonicKtorServlet(), "/internal/ktor/*");
        registration.setName("ktor");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        registration.addInitParameter("io.ktor.ktor.config", "ktor-application.conf");
        return registration;
    }
}
