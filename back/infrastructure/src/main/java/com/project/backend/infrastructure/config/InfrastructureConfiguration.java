package com.project.backend.infrastructure.config;

import com.project.backend.infrastructure.security.AuthenticationRateLimitProperties;
import com.project.backend.infrastructure.security.JwtAuthenticationProperties;
import com.project.backend.infrastructure.security.RegistrationRateLimitProperties;
import com.project.backend.infrastructure.notification.OutboundEmailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        JwtAuthenticationProperties.class,
        AuthenticationRateLimitProperties.class,
        RegistrationRateLimitProperties.class,
        OutboundEmailProperties.class})
public class InfrastructureConfiguration {
}
