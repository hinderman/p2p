/**
 * Infrastructure technical configuration.
 *
 * <p>Beans de acceso a datos: {@code @EnableJpaRepositories}, {@code @EntityScan},
 * gestion de transacciones, pools de conexion, cache, clientes HTTP.
 *
 * <p>Solo configuracion de <strong>adaptadores</strong>. El ensamblado de los casos
 * de uso es responsabilidad del composition root, en {@code api.config}.
 */
package com.project.backend.infrastructure.config;
