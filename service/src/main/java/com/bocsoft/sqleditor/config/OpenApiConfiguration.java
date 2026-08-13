package com.bocsoft.sqleditor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI sqlEditorOpenApi() {
        return new OpenAPI()
            .info(new Info().title("Zorth Web SQL Service API").version("v1"))
            .schemaRequirement("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("Token"));
    }
}
