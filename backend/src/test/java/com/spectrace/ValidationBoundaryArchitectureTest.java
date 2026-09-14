package com.spectrace;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.spectrace")
class ValidationBoundaryArchitectureTest {

    @ArchTest
    static final ArchRule validation_application_does_not_depend_on_adapters = noClasses()
            .that().resideInAnyPackage("com.spectrace.validation.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.spectrace..infrastructure..");

    @ArchTest
    static final ArchRule validation_application_does_not_reach_foreign_adapters = noClasses()
            .that().resideInAnyPackage("com.spectrace.validation.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.spectrace.catalog.infrastructure..",
                    "com.spectrace.allergen.infrastructure..",
                    "com.spectrace.identity.infrastructure..",
                    "com.spectrace.label.infrastructure..",
                    "com.spectrace.workflow.infrastructure..",
                    "com.spectrace.audit.infrastructure..",
                    "com.spectrace.dashboard.infrastructure..",
                    "com.spectrace.impact.infrastructure..");
}
