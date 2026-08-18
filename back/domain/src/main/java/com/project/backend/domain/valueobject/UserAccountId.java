package com.project.backend.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Identidad de una account autenticable, distinta de la identidad de person. */
public record UserAccountId(UUID value) implements ValueObject {
    public UserAccountId {
        Objects.requireNonNull(value, "El identificador de account es obligatorio");
    }
}
