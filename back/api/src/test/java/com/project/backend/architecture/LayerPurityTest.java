package com.project.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Comprueba que cada capa contiene lo que le toca y nada mas.
 *
 * <p>Varias reglas usan {@code allowEmptyShould(true)} porque el esqueleto todavia
 * no tiene entidades JPA ni controladores. Sin eso ArchUnit fallaria por no
 * encontrar clases que evaluar. En cuanto aparezcan, las reglas empiezan a morder
 * solas.
 */
@AnalyzeClasses(
        packages = "com.project.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayerPurityTest {

    private static final String[] FRAMEWORKS = {
            "org.springframework..",
            "jakarta.persistence..",
            "jakarta.validation..",
            "com.fasterxml.jackson..",
            "tools.jackson..",
            "org.hibernate.."
    };

    /** The domain is plain Java: no ORM, web, or serialization dependencies. */
    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("com.project.backend.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS);

    /** La capa de allocation tampoco conoce el framework: se ensambla desde api. */
    @ArchTest
    static final ArchRule application_is_framework_free = noClasses()
            .that().resideInAPackage("com.project.backend.application..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS);

    /** JPA entities are a persistence detail, never part of the domain. */
    @ArchTest
    static final ArchRule jpa_entities_are_only_in_persistence = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage("com.project.backend.infrastructure.persistence.entity..")
            .allowEmptyShould(true);

    /** Los controladores solo viven en el adaptador REST. */
    @ArchTest
    static final ArchRule controllers_are_only_in_api_rest = classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage("com.project.backend.api.rest..")
            .allowEmptyShould(true);

    /** Domain persistence ports are contracts, not implementations. */
    @ArchTest
    static final ArchRule domain_ports_are_interfaces = classes()
            .that().resideInAPackage("com.project.backend.domain.repository..")
            .should().beInterfaces()
            .allowEmptyShould(true);

    /** Los puertos de output de allocation tambien son contratos. */
    @ArchTest
    static final ArchRule outbound_ports_are_interfaces = classes()
            .that().resideInAPackage("com.project.backend.application.port..")
            .should().beInterfaces()
            .allowEmptyShould(true);
}
