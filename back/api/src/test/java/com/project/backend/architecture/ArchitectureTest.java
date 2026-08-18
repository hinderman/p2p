package com.project.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Verifica la Arquitectura Limpia como test ejecutable.
 *
 * <p>La separacion en modulos Maven ya impide que {@code domain} vea Spring, pero
 * no puede impedir que {@code api} llame directamente a {@code infrastructure}:
 * ambos estan en el mismo classpath. Esa clase de reglas se comprueba aqui.
 */
@AnalyzeClasses(
        packages = "com.project.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * Regla de dependencias: las flechas apuntan hacia dentro.
     *
     * <p>Lo importante son las dos primeras terms:
     * <ul>
     *   <li>Nadie accede a {@code api}: es la frontera exterior.</li>
     *   <li>Nadie accede a {@code infrastructure}, <strong>ni siquiera api</strong>.
     *       The composition root wires domain interfaces; Spring
     *       inyecta la implementacion en tiempo de ejecucion. Si algun controlador
     *       importara un repositorio JPA, este test lo cazaria.</li>
     * </ul>
     */
    @ArchTest
    static final ArchRule dependency_rule = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("com.project.backend.domain..")
            .layer("Application").definedBy("com.project.backend.application..")
            .layer("Infrastructure").definedBy("com.project.backend.infrastructure..")
            .layer("Api").definedBy("com.project.backend.api..")

            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Api");

    /** Ninguna capa puede formar ciclos con other. */
    @ArchTest
    static final ArchRule no_cycles_between_layers = slices()
            .matching("com.project.backend.(*)..")
            .should().beFreeOfCycles();
}
