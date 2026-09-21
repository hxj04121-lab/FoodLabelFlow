package com.spectrace.validation;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.spectrace", importOptions = ImportOption.DoNotIncludeTests.class)
class ValidationBoundaryArchitectureTest {
    @ArchTest
    static final ArchRule validation_web_uses_application_boundaries_not_storage = noClasses()
            .that().resideInAnyPackage("com.spectrace.validation.interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "org.springframework.jdbc..", "java.sql..", "javax.sql..");

    @ArchTest
    static final ArchRule contracts_and_rules_do_not_access_storage_or_web = noClasses()
            .that().resideInAnyPackage(
                    "com.spectrace.label.application.port..", "com.spectrace.catalog.application.port..",
                    "com.spectrace.allergen.application.port..", "com.spectrace.validation.application.port..",
                    "com.spectrace.validation.application.rule..", "com.spectrace.validation.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "..interfaces..", "org.springframework..",
                    "java.sql..", "javax.sql..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule validation_and_allergen_do_not_access_foreign_repositories = noClasses()
            .that().resideInAnyPackage("com.spectrace.validation..", "com.spectrace.allergen..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.spectrace.catalog.infrastructure..", "com.spectrace.label.infrastructure..",
                    "com.spectrace.identity.infrastructure..", "com.spectrace.audit.infrastructure..");

    @ArchTest
    static final ArchRule foreign_repository_interfaces_are_not_cross_module_ports = noClasses()
            .that().resideInAnyPackage("com.spectrace.validation..", "com.spectrace.allergen..")
            .should().dependOnClassesThat(resideInAnyPackage(
                    "com.spectrace.catalog..", "com.spectrace.label..",
                    "com.spectrace.identity..", "com.spectrace.audit..")
                    .and(simpleNameEndingWith("Repository").or(simpleNameEndingWith("Store"))));
}
