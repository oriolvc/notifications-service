package com.oriolvc.notificationsservice.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the OpenAPI spec and the parameter schemas it {@code $ref}s, with the same relative layout as on the
 * classpath, so Swagger UI can resolve {@code ../schemas/*.json}.
 */
@Configuration(proxyBeanMethods = false)
class ApiDocumentationConfig implements WebMvcConfigurer {

    static final String SPEC_PATH = "/openapi/notifications-service.yaml";

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/openapi/**").addResourceLocations("classpath:/openapi/");
        registry.addResourceHandler("/schemas/**").addResourceLocations("classpath:/schemas/");
    }
}
