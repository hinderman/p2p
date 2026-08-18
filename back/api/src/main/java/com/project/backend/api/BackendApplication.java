package com.project.backend.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de input y <strong>composition root</strong> de la allocation.
 *
 * <p>Es el unico lugar que conoce a la vez los casos de uso ({@code application})
 * y sus adaptadores ({@code infrastructure}), y por tanto el unico que puede
 * ensamblarlos.
 *
 * <p>Se escanean explicitamente los dos unicos paquetes que aportan beans. No se
 * incluyen {@code domain} ni {@code application}: por diseno no tienen anotaciones
 * de Spring, asi que barrerlos solo costaria tiempo de arranque. Sus objetos se
 * declaran a mano en {@code api.config}.
 */
@SpringBootApplication(scanBasePackages = {
        "com.project.backend.api",
        "com.project.backend.infrastructure"
})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
