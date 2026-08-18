/**
 * HTTP output DTOs: data serialized for the client.
 *
 * <p>They are immutable {@code record}s. Domain or JPA entities are never returned
 * directly because that would expose internal structure and couple the public API
 * contract to internal decisions.
 *
 * <p>This layer determines which internal or sensitive fields remain hidden.
 */
package com.project.backend.api.dto.response;
