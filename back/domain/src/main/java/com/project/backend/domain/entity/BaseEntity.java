package com.project.backend.domain.entity;

import java.util.Objects;

/**
 * Base class for every domain entity.
 *
 * <p>Una entidad se distingue por su <strong>identidad</strong>, no por sus atributos:
 * dos entidades con el mismo id son la misma entidad aunque su contenido difiera.
 * Por eso {@code equals} y {@code hashCode} se basan unicamente en el id.
 *
 * @param <ID> type del identificador (habitualmente un value object)
 */
public abstract class BaseEntity<ID> {

    private final ID id;

    protected BaseEntity(ID id) {
        this.id = Objects.requireNonNull(id, "La identidad de una entidad no puede ser nula");
    }

    public ID id() {
        return id;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BaseEntity<?> other)) {
            return false;
        }
        return getClass() == other.getClass() && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass(), id);
    }

    @Override
    public String toString() {
        return "%s[id=%s]".formatted(getClass().getSimpleName(), id);
    }
}
