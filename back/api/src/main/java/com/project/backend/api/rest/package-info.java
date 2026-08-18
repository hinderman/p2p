/**
 * REST input adapters: {@code @RestController}s.
 *
 * <p>A controller does exactly three things: receives the request, invokes a use case,
 * and returns the response. It contains <strong>no business logic</strong>; a conditional
 * implementing domain rules belongs elsewhere.
 *
 * <p>It depends on {@code application.port.in}, never on {@code infrastructure}; the
 * controller does not know whether PostgreSQL, a file, or an external API is behind it.
 */
package com.project.backend.api.rest;
