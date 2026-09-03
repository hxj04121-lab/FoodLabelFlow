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
}
