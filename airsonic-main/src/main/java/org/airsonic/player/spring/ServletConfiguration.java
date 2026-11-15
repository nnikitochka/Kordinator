package org.airsonic.player.spring;

import org.airsonic.player.filter.BootstrapVerificationFilter;
import org.airsonic.player.filter.MetricsFilter;
import org.airsonic.player.filter.ParameterDecodingFilter;
import org.airsonic.player.filter.RESTFilter;
import org.airsonic.player.filter.RequestEncodingFilter;
import org.airsonic.player.filter.ResponseHeaderFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.Filter;


@Configuration
public class ServletConfiguration implements WebMvcConfigurer {
    public ServletConfiguration(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Bean
    public LegacyViewSuffixHandlerMapping legacyViewSuffixHandlerMapping() {
        return new LegacyViewSuffixHandlerMapping();
    }

    @Bean
    public FilterRegistrationBean<Filter> bootstrapVerificationFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(bootstrapVerificationFilter());
        registration.addUrlPatterns("/*");
        registration.setName("BootstrapVerificationFilter");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public Filter bootstrapVerificationFilter() {
        return new BootstrapVerificationFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> parameterDecodingFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(parameterDecodingFilter());
        registration.addUrlPatterns("/*");
        registration.setName("ParameterDecodingFilter");
        registration.setOrder(2);
        return registration;
    }

    @Bean
    public Filter parameterDecodingFilter() {
        return new ParameterDecodingFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> restFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(restFilter());
        registration.addUrlPatterns("/rest/*");
        registration.setName("RESTFilter");
        registration.setOrder(3);
        return registration;
    }

    @Bean
    public Filter restFilter() {
        return new RESTFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> requestEncodingFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(requestEncodingFilter());
        registration.addUrlPatterns("/*");
        registration.addInitParameter("encoding", "UTF-8");
        registration.setName("RequestEncodingFilter");
        registration.setOrder(4);
        return registration;
    }

    @Bean
    public Filter requestEncodingFilter() {
        return new RequestEncodingFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> cacheFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(cacheFilter());
        registration.addUrlPatterns("/icons/*", "/style/*", "/script/*", "/icons/*", "/coverArt.view", "/avatar.view");
        registration.addInitParameter("Cache-Control", "max-age=36000");
        registration.setName("CacheFilter");
        registration.setOrder(5);
        return registration;
    }

    @Bean
    public Filter cacheFilter() {
        return new ResponseHeaderFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> noCacheFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(noCacheFilter());
        registration.addUrlPatterns("/statusChart.view", "/userChart.view", "/playQueue.view", "/help.view", "/top.view", "/home.view");
        registration.addInitParameter("Cache-Control", "no-cache, post-check=0, pre-check=0");
        registration.addInitParameter("Pragma", "no-cache");
        registration.addInitParameter("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        registration.setName("NoCacheFilter");
        registration.setOrder(6);
        return registration;
    }

    @Bean
    public Filter noCacheFilter() {
        return new ResponseHeaderFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> metricsFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(metricsFilter());
        registration.setOrder(7);
        return registration;
    }

    @Bean
    public Filter metricsFilter() {
        return new MetricsFilter();
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
