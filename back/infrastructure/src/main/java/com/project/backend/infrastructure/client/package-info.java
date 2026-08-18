/**
 * Adaptadores hacia sistemas externos: APIs de terceros, colas, email, almacenamiento.
 *
 * <p>Cada clase implementa un puerto de output declarado en
 * {@code application.port.out}. La allocation depende del puerto, nunca del cliente
 * concreto, asi que cambiar de proveedor se reduce a escribir other adaptador.
 */
package com.project.backend.infrastructure.client;
