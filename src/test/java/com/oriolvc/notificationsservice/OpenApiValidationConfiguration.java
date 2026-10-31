package com.oriolvc.notificationsservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class OpenApiValidationConfiguration {

    @Bean
    MockMvcBuilderCustomizer openApiContractValidation() {
        return builder -> builder.alwaysExpect(new OpenApiContractMatcher());
    }
}
