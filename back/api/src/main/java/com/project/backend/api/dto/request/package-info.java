/**
 * HTTP input DTOs: request bodies.
 *
 * <p>They are {@code record}s with Bean Validation annotations ({@code @NotBlank},
 * {@code @Size}) that reject invalid input at the boundary before it reaches the domain.
 *
 * <p>They are mapped to application commands or queries in {@code api.mapper}. Keeping
 * them separate from {@code application.dto} allows the REST API to evolve without
 * changing use cases.
 */
package com.project.backend.api.dto.request;
