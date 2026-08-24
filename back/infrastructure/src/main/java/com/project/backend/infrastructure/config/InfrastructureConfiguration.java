package com.project.backend.infrastructure.config;

import com.project.backend.infrastructure.security.AuthenticationRateLimitProperties;
import com.project.backend.infrastructure.security.JwtAuthenticationProperties;
import com.project.backend.infrastructure.notification.InvitationEmailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        JwtAuthenticationProperties.class,
        AuthenticationRateLimitProperties.class,
        InvitationEmailProperties.class})
public class InfrastructureConfiguration {
}
