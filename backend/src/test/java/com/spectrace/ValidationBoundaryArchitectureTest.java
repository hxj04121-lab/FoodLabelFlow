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
    static final ArchRule validation_application_does_not_reach_foreign_modules = noClasses()
            .that().resideInAnyPackage("com.spectrace.validation.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.spectrace.catalog..",
                    "com.spectrace.allergen..",
                    "com.spectrace.identity..",
                    "com.spectrace.label..",
                    "com.spectrace.workflow..",
                    "com.spectrace.audit..",
                    "com.spectrace.dashboard..",
                    "com.spectrace.impact..");
}
