package com.clinconnect.chatbot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Restrictive local CORS per docs/09-SECURITY.md: origin comes from
 * configuration (ALLOWED_ORIGINS), never wildcarded, and is not deferred to
 * a later hardening phase.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String allowedOrigin;

    public CorsConfig(@Value("${clinconnect.cors.allowed-origins}") String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }

    public String getAllowedOrigin() {
        return allowedOrigin;
    }
}
