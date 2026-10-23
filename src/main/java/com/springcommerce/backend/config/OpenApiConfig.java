package com.springcommerce.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI agoranOpenAPI(){

        return new OpenAPI()
                .info(
                        new Info()
                                .title("Agora E-commerce API")
                                .version("1.0.0")
                                .description("""
                                        Agora is a modern e-commerce REST API
                                        developed using Java 21 and Spring Boot 4.
                                        
                                        Features includes:
                                        
                                        - Authentication and authorization
                                        - Product management
                                        - Product categories
                                        - Shopping cart
                                        - Wishlist
                                        - Customer orders
                                        - Inventory management
                                        - Customer reviews
                                        - Payment integration
                                        - Administrative operations
                                        
                                        Authentication:
                                        Use the Authorize button to provide
                                        a valid JWT access token.
                                        """)
                                .contact(
                                        new Contact()
                                                .name("Eddy Mouity")
                                                .url("https://www.eddymouity.dev/en")
                                )
                )
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(SECURITY_SCHEME_NAME)
                )
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_NAME,
                                        new SecurityScheme()
                                                .name(SECURITY_SCHEME_NAME)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "Enter your JWT access token."
                                                )
                                )
                );
    }
}
