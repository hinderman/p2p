package com.project.backend.infrastructure.config;

import com.project.backend.infrastructure.security.JwtAuthenticationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtAuthenticationProperties.class)
public class InfrastructureConfiguration {
}
