package com.project.backend.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.application.security.AuthenticatedAccessToken;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Strict HS512 JWT encoder/decoder with no algorithm negotiation. */
final class JwtAccessTokenCodec {
    private static final int MAX_TOKEN_LENGTH = 8_192;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String issuer;
    private final byte[] signingKey;

    JwtAccessTokenCodec(JwtAuthenticationProperties properties) {
        this.issuer = properties.issuer();
        this.signingKey = properties.decodedHmacSecret();
    }

    String issue(UserAccount account, UUID sessionId, Instant issuedAt, Instant expiresAt) {
        try {
            String header = base64Url(JSON.writeValueAsBytes(java.util.Map.of("alg", "HS512", "typ", "JWT")));
            Set<String> roles = account.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
            java.util.Map<String, Object> claims = new java.util.LinkedHashMap<>();
            claims.put("iss", issuer);
            claims.put("sub", account.id().value().toString());
            claims.put("person_id", account.personId().value().toString());
            claims.put("session_id", sessionId.toString());
            claims.put("roles", roles);
            claims.put("authorization_version", account.authorizationVersion());
            claims.put("iat", issuedAt.getEpochSecond());
            claims.put("nbf", issuedAt.getEpochSecond());
            claims.put("exp", expiresAt.getEpochSecond());
            claims.put("jti", UUID.randomUUID().toString());
            String content = header + "." + base64Url(JSON.writeValueAsBytes(claims));
            return content + "." + URL_ENCODER.encodeToString(hmacSha512(content));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue an access token", exception);
        }
    }

    AuthenticatedAccessToken decodeAndVerify(String token, Instant now) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("The access token is invalid");
        }
        try {
            String[] segments = token.split("\\.", -1);
            if (segments.length != 3 || segments[0].isEmpty() || segments[1].isEmpty() || segments[2].isEmpty()) {
                throw new IllegalArgumentException("The access token is invalid");
            }
            byte[] providedSignature = URL_DECODER.decode(segments[2]);
            byte[] expectedSignature = hmacSha512(segments[0] + "." + segments[1]);
            if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
                throw new IllegalArgumentException("The access token signature is invalid");
            }
            JsonNode header = JSON.readTree(URL_DECODER.decode(segments[0]));
            if (!"HS512".equals(requiredText(header, "alg")) || !"JWT".equals(requiredText(header, "typ"))) {
                throw new IllegalArgumentException("The access token header is invalid");
            }
            JsonNode claims = JSON.readTree(URL_DECODER.decode(segments[1]));
            if (!issuer.equals(requiredText(claims, "iss"))) {
                throw new IllegalArgumentException("The access token issuer is invalid");
            }
            Instant issuedAt = Instant.ofEpochSecond(requiredLong(claims, "iat"));
            Instant notBefore = Instant.ofEpochSecond(requiredLong(claims, "nbf"));
            Instant expiresAt = Instant.ofEpochSecond(requiredLong(claims, "exp"));
            if (notBefore.isAfter(now) || !expiresAt.isAfter(now) || issuedAt.isAfter(now.plusSeconds(30)) || !expiresAt.isAfter(issuedAt)) {
                throw new IllegalArgumentException("The access token is outside its validity period");
            }
            Set<UserRole> roles = roles(claims.path("roles"));
            return new AuthenticatedAccessToken(
                    new UserAccountId(UUID.fromString(requiredText(claims, "sub"))),
                    new PersonId(UUID.fromString(requiredText(claims, "person_id"))),
                    UUID.fromString(requiredText(claims, "session_id")), roles,
                    requiredLong(claims, "authorization_version"), issuedAt, expiresAt);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("The access token is invalid", exception);
        }
    }

    private byte[] hmacSha512(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA512"));
            return mac.doFinal(content.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify an access token", exception);
        }
    }

    private static Set<UserRole> roles(JsonNode roles) {
        if (!roles.isArray() || roles.isEmpty()) {
            throw new IllegalArgumentException("The access token roles are invalid");
        }
        EnumSet<UserRole> result = EnumSet.noneOf(UserRole.class);
        for (JsonNode role : roles) {
            if (!role.isTextual() || !result.add(UserRole.valueOf(role.textValue()))) {
                throw new IllegalArgumentException("The access token roles are invalid");
            }
        }
        return Set.copyOf(result);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("The access token claim is invalid");
        }
        return value.textValue();
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.canConvertToLong() || value.asLong() < 0) {
            throw new IllegalArgumentException("The access token claim is invalid");
        }
        return value.asLong();
    }

    private static String base64Url(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }
}
