package com.stage8.wallet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI walletServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wallet Service API")
                        .description("""
                                A comprehensive wallet service API with Google OAuth authentication, 
                                API key management, Paystack payment integration, and wallet-to-wallet transfers.
                                
                                ## Authentication
                                This API supports two authentication methods:
                                
                                1. **JWT Authentication**: Use the JWT token obtained from Google OAuth login
                                   - Header: `Authorization: Bearer <token>`
                                
                                2. **API Key Authentication**: Use API keys for service-to-service communication
                                   - Header: `x-api-key: <your-api-key>`
                                   - API keys have granular permissions (DEPOSIT, TRANSFER, READ)
                                
                                ## Amount Format
                                **IMPORTANT**: All amounts are in **kobo** (smallest Nigerian currency unit).
                                - 1 Naira = 100 kobo
                                - Example: 5000 kobo = 50 Naira
                                
                                ## Getting Started
                                1. Authenticate via Google OAuth: `GET /auth/google`
                                2. Use the returned JWT token for authenticated requests
                                3. Or create an API key: `POST /keys/create`
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Wallet Service")
                                .email("support@wallet.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("https://your-app.railway.app")
                                .description("Production Server (Railway)")
                ))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token obtained from Google OAuth login. Format: Bearer <token>"))
                        .addSecuritySchemes("API Key Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("x-api-key")
                                .description("API key for service-to-service authentication. Format: <api-key>")));
    }
}
