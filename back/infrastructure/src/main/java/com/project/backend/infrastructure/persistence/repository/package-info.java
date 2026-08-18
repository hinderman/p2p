/**
 * Persistence adapters that implement {@code domain.repository} ports.
 *
 * <p>Contains two distinct concerns:
 * <ul>
 *   <li>Spring Data {@code JpaRepository} interfaces, a technical detail that works
 *       with JPA entities.</li>
 *   <li>Adapters that implement the domain port by delegating to those interfaces and
 *       mapping JPA entity &harr; aggregate.</li>
 * </ul>
 *
 * <p>The domain knows only its interface and never sees {@code JpaRepository}.
 */
package com.project.backend.infrastructure.persistence.repository;
