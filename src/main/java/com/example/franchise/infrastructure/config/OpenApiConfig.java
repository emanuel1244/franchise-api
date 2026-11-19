package com.example.franchise.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI franchiseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Franchise API")
                .version("1.0.0")
                .description("API reactiva para gestionar franquicias, sucursales y productos sobre DynamoDB"));
    }
}
