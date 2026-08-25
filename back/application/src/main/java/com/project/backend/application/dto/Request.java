package com.project.backend.application.dto;

/**
 * A typed application intent with exactly one result type.
 *
 * <p>The type parameter makes dispatch through the application mediator safe at
 * the call site: a {@code CreateLoanCommand} can only produce a
 * {@code LoanCreated}, while a query retains its own projection type.
 * Commands and queries remain distinct specializations to preserve CQRS.
 *
 * @param <R> result returned by the request handler
 */
public interface Request<R> {
}
