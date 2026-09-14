package com.spectrace;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.spectrace")
class ArchitectureTest {

    @ArchTest
    static final ArchRule identity_does_not_depend_on_business_modules = noClasses()
            .that().resideInAnyPackage("com.spectrace.identity..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.spectrace.catalog..",
                    "com.spectrace.allergen..",
                    "com.spectrace.label..",
                    "com.spectrace.validation..",
                    "com.spectrace.impact..",
                    "com.spectrace.workflow..",
                    "com.spectrace.audit..",
                    "com.spectrace.dashboard.."
            );

    @ArchTest
    static final ArchRule validation_uses_application_ports_not_foreign_adapters = noClasses()
            .that().resideInAnyPackage("com.spectrace.validation.application..", "com.spectrace.validation.interfaces..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.spectrace.catalog.infrastructure..",
                    "com.spectrace.identity.infrastructure..",
                    "com.spectrace.label.infrastructure..",
                    "com.spectrace.allergen.infrastructure..",
                    "com.spectrace.audit.infrastructure.."
            );

    @ArchTest
    static final ArchRule validation_has_no_foreign_repository_or_sql_shortcut = noClasses()
            .that().resideInAnyPackage("com.spectrace.validation..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.spectrace.catalog.infrastructure.CatalogStore",
                    "com.spectrace.identity.infrastructure.JdbcIdentityRepository"
            );
}
