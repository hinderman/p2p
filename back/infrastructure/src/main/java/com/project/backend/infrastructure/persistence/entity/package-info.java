/**
 * JPA entities: the <strong>persistence</strong> model, not the domain model.
 *
 * <p>This package contains classes annotated with {@code @Entity}, {@code @Table},
 * and {@code @Column}. They are deliberately <strong>different</strong> from
 * {@code domain.entity} classes: annotating the domain with JPA would couple it to
 * the ORM and table shape, which this architecture avoids.
 *
 * <p>This separation lets the database schema evolve (renaming columns,
 * denormalizing, or changing engines) without changing a domain line.
 *
 * <p>Translation between the models lives in {@code infrastructure.persistence.mapper}.
 */
package com.project.backend.infrastructure.persistence.entity;
