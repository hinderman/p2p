package com.project.backend.application.port.in;

/**
 * Puerto de input: un caso de uso de la allocation.
 *
 * <p>Es el contrato que la capa api invoca. Un caso de uso hace <strong>una sola
 * cosa</strong> y se nombra con la accion del negocio ({@code RegistrarUsuario},
 * {@code ConfirmarPedido}).
 *
 * <p>La implementacion vive en {@code application.usecase} y no conoce HTTP:
 * recibe un comando o consulta y devuelve un resultado.
 *
 * @param <E> type de input (comando o consulta)
 * @param <S> type de output
 */
@FunctionalInterface
public interface UseCase<E, S> {

    S execute(E input);
}
