package com.cms.adapters.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CMS Backend API")
                        .description("REST API for CMS - Content Management System")
                        .version("v1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(SecurityConstants.SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SecurityConstants.SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SecurityConstants.SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme(SecurityConstants.BEARER_SCHEME)
                                        .bearerFormat(SecurityConstants.JWT_FORMAT)));
    }
}
