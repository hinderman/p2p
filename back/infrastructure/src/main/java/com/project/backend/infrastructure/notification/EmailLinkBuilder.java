package com.project.backend.infrastructure.notification;

import com.project.backend.application.dto.OutboundEmailKind;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds fragment-based URLs so a token never reaches web-server access logs or
 * {@code Referer} headers. The frontend reads the fragment, clears it with
 * {@code history.replaceState}, and submits the token in a request body.
 */
final class EmailLinkBuilder {
    private final OutboundEmailProperties properties;

    EmailLinkBuilder(OutboundEmailProperties properties) {
        this.properties = properties;
    }

    String build(OutboundEmailKind kind, String rawToken) {
        return baseUrl() + path(kind) + "#" + fragmentParameter(kind) + "="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    String signInUrl() {
        return baseUrl() + "/login";
    }

    private String baseUrl() {
        return properties.publicBaseUrl().toString().replaceAll("/+$", "");
    }

    private String path(OutboundEmailKind kind) {
        return switch (kind) {
            case LOAN_INVITATION -> properties.onboardingPath();
            case ACCOUNT_EMAIL_VERIFICATION -> properties.verificationPath();
            case EXISTING_ACCOUNT_NOTICE -> throw new IllegalArgumentException("%s carries no link".formatted(kind));
        };
    }

    private static String fragmentParameter(OutboundEmailKind kind) {
        return switch (kind) {
            case LOAN_INVITATION -> "invitationToken";
            case ACCOUNT_EMAIL_VERIFICATION -> "verificationToken";
            case EXISTING_ACCOUNT_NOTICE -> throw new IllegalArgumentException("%s carries no link".formatted(kind));
        };
    }
}
