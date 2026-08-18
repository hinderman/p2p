/**
 * Implementations of use cases ({@code application.port.in}).
 *
 * <p>Each class orchestrates a business flow: it loads aggregates through repositories,
 * asks them to apply their rules, and persists the result. Business logic itself
 * <strong>lives in the domain</strong>; when a use case accumulates rules and state,
 * that logic belongs in an entity or domain service.
 *
 * <p>These are plain classes without framework annotations. They are registered as
 * beans from the API layer composition root and can be tested directly with test doubles.
 */
package com.project.backend.application.usecase;
