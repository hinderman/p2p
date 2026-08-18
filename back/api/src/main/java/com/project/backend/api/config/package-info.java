/**
 * Composition-root configuration.
 *
 * <p>Use-case implementations are declared here as {@code @Bean} definitions and
 * receive their ports through dependency injection. Because {@code application} has
 * no Spring annotations, this is where the object graph is assembled:
 *
 * <pre>{@code
 * @Bean
 * CreateLoanHandler createLoanHandler(LoanRepository loanRepository) {
 *     return new CreateLoanHandler(loanRepository);
 * }
 * }</pre>
 *
 * <p>This package also contains web-layer configuration such as CORS, serialization,
 * security, and API documentation.
 */
package com.project.backend.api.config;
