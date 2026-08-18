/**
 * Output ports: what the application <strong>needs</strong> from the outside world.
 *
 * <p>They are interfaces declared here and implemented by {@code infrastructure}
 * adapters. Typical examples include email delivery, domain-event publishing, the
 * system clock, a payment gateway, and an external API client.
 *
 * <p>This is the <strong>dependency inversion</strong> that sustains the architecture:
 * the application defines the contract and infrastructure adapts to it, never the reverse.
 *
 * <p>Aggregate repositories do not belong here: their ports live in
 * {@code domain.repository} because they are part of the domain language.
 */
package com.project.backend.application.port.out;
