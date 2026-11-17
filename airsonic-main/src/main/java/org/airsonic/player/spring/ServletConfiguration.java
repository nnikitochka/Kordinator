package org.airsonic.player.spring;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ServletConfiguration implements WebMvcConfigurer {
    public ServletConfiguration(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Bean
    public LegacyViewSuffixHandlerMapping legacyViewSuffixHandlerMapping() {
        return new LegacyViewSuffixHandlerMapping();
    }

    @Bean(destroyMethod = "close")
    public org.airsonic.player.monitor.MetricsManager metricsManager(org.airsonic.player.runtime.RuntimeCompositionRoot runtime) {
        org.airsonic.player.monitor.MetricsManager manager = new org.airsonic.player.monitor.MetricsManager(runtime.getConfiguration());
        runtime.getLifecycleRegistry().registerShutdown("metricsManager", manager);
        return manager;
    }

    private final MessageSource messageSource;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Spring Framework 6 disabled trailing-slash matching by default; re-enable it to
        // preserve the URL behavior of the previous platform (Boot 2.5 / Framework 5.3).
        configurer.setUseTrailingSlashMatch(true);
    }

    @Override
    public Validator getValidator() {
        // need to get constraint violation message codes translated
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }
}
