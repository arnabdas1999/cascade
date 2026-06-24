package com.cascade.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cascadeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cascade Workflow Engine API")
                        .description("Define, run, and monitor durable multi-step workflows")
                        .version("0.1.0")
                        .license(new License().name("MIT")));
    }
}
