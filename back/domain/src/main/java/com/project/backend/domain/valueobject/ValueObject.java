package com.project.backend.domain.valueobject;

/**
 * Marks a domain value object.
 *
 * <p>A diferencia de una entidad, un value object <strong>no tiene identidad</strong>:
 * se define por sus atributos y es inmutable. Dos value objects con los mismos
 * valores son intercambiables.
 *
 * <p>La forma natural de implementarlo en Java moderno es un {@code record},
 * que ya aporta inmutabilidad, {@code equals}, {@code hashCode} y {@code toString}.
 * La validacion de invariantes va en el constructor compacto:
 *
 * <pre>{@code
 * public record Email(String value) implements ValueObject {
 *     public Email {
 *         if (value == null || !value.contains("@")) {
 *             throw new IllegalArgumentException("Email invalido");
 *         }
 *     }
 * }
 * }</pre>
 */
public interface ValueObject {
}
