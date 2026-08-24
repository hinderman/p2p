package com.project.backend.infrastructure.notification;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Builds a fragment-based URL so the token is not sent to web-server or referrer logs. */
final class InvitationLinkBuilder {
    private final InvitationEmailProperties properties;

    InvitationLinkBuilder(InvitationEmailProperties properties) {
        this.properties = properties;
    }

    String build(String rawToken) {
        String baseUrl = properties.publicBaseUrl().toString().replaceAll("/+$", "");
        return baseUrl + properties.onboardingPath() + "#invitationToken="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
