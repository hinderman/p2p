/**
 * Conversion between application DTOs ({@code Command}, {@code Query}, and results)
 * and domain objects.
 *
 * <p>Keeping this translation explicit prevents commands from leaking into the domain
 * and entities from leaking outward. The domain is never exposed as-is externally.
 */
package com.project.backend.application.mapper;
