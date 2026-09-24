package com.spectrace;

import com.spectrace.archfixture.validation.application.BadValidationService;
import com.spectrace.archfixture.validation.application.GoodValidationService;
import com.spectrace.archfixture.validation.interfaces.BadValidationController;
import com.spectrace.allergen.application.port.AllergenFactsPort;
import com.spectrace.audit.application.port.AuditEventPort;
import com.spectrace.label.application.port.LabelSnapshotPort;
import com.spectrace.validation.application.port.AuthorizationPort;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(packages = "com.spectrace", importOptions = ImportOption.DoNotIncludeTests.class)
class ValidationArchitectureTest {
    private static final String VALIDATION_PACKAGE = "com.spectrace.validation";
    private static final String[] FOREIGN_MODULE_PACKAGES = {
            "com.spectrace.catalog..",
            "com.spectrace.identity..",
            "com.spectrace.label..",
            "com.spectrace.allergen..",
            "com.spectrace.workflow..",
            "com.spectrace.audit..",
            "com.spectrace.dashboard..",
            "com.spectrace.impact.."
    };

    @ArchTest
    static final ArchRule validation_domain_is_pure = validationDomainIsPure(VALIDATION_PACKAGE);

    @ArchTest
    static final ArchRule validation_application_does_not_depend_on_infrastructure =
            validationPackagesDoNotDependOnInfrastructure(VALIDATION_PACKAGE + ".application..");

    @ArchTest
    static final ArchRule validation_interfaces_use_application_boundaries =
            validationPackagesDoNotDependOnInfrastructure(VALIDATION_PACKAGE + ".interfaces..");

    @ArchTest
    static final ArchRule validation_core_does_not_depend_on_jdbc_or_jpa =
            validationCoreDoesNotDependOnJdbcOrJpa(VALIDATION_PACKAGE);

    @ArchTest
    static final ArchRule validation_core_does_not_depend_on_foreign_repository_adapters =
            validationCoreDoesNotDependOnForeignRepositoryAdapters(
                    VALIDATION_PACKAGE, FOREIGN_MODULE_PACKAGES);

    @ArchTest
    static final ArchRule validation_and_allergen_do_not_access_foreign_repositories = noClasses()
            .that().resideInAnyPackage("com.spectrace.validation..", "com.spectrace.allergen..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.spectrace.catalog.infrastructure..",
                    "com.spectrace.label.infrastructure..",
                    "com.spectrace.identity.infrastructure..",
                    "com.spectrace.audit.infrastructure..",
                    "com.spectrace.workflow.infrastructure..");

    @ArchTest
    static final ArchRule contracts_and_rules_do_not_access_storage_or_web = noClasses()
            .that().resideInAnyPackage(
                    "com.spectrace.label.application.port..",
                    "com.spectrace.catalog.application.port..",
                    "com.spectrace.allergen.application.port..",
                    "com.spectrace.validation.application.port..",
                    "com.spectrace.validation.application.rule..",
                    "com.spectrace.validation.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "..interfaces..", "org.springframework..",
                    "java.sql..", "javax.sql..", "jakarta.persistence..",
                    "javax.persistence..", "org.springframework.data.jpa..");

    @Test
    void rules_reject_foreign_infrastructure_jdbc_and_repository_fixtures() {
        JavaClasses fixtures = new ClassFileImporter().importPackages("com.spectrace.archfixture");

        assertThat(validationCoreDoesNotDependOnInfrastructure(
                "com.spectrace.archfixture.validation").evaluate(fixtures).hasViolation()).isTrue();
        assertThat(validationCoreDoesNotDependOnJdbcOrJpa(
                "com.spectrace.archfixture.validation").evaluate(fixtures).hasViolation()).isTrue();
        assertThat(validationCoreDoesNotDependOnForeignRepositoryAdapters(
                "com.spectrace.archfixture.validation",
                "com.spectrace.archfixture.foreign..")
                .evaluate(fixtures).hasViolation()).isTrue();

    }

    @Test
    void legitimate_port_dependencies_are_not_rejected() {
        JavaClasses fixtures = new ClassFileImporter().importClasses(
                GoodValidationService.class,
                LabelSnapshotPort.class,
                AllergenFactsPort.class,
                AuthorizationPort.class,
                AuditEventPort.class);

        assertThat(validationCoreDoesNotDependOnInfrastructure(
                "com.spectrace.archfixture.validation").evaluate(fixtures).hasViolation()).isFalse();
        assertThat(validationCoreDoesNotDependOnJdbcOrJpa(
                "com.spectrace.archfixture.validation").evaluate(fixtures).hasViolation()).isFalse();
        assertThat(validationCoreDoesNotDependOnForeignRepositoryAdapters(
                "com.spectrace.archfixture.validation",
                "com.spectrace.archfixture.foreign..")
                .evaluate(fixtures).hasViolation()).isFalse();
    }

    private static ArchRule validationDomainIsPure(String validationPackage) {
        return noClasses()
                .that().resideInAnyPackage(validationPackage + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..", "org.springframework..", "java.sql..", "javax.sql..",
                        "jakarta.persistence..", "javax.persistence..", "org.springframework.data.jpa..");
    }

    private static ArchRule validationCoreDoesNotDependOnInfrastructure(String validationPackage) {
        return validationPackagesDoNotDependOnInfrastructure(
                validationPackage + ".domain..",
                validationPackage + ".application..",
                validationPackage + ".interfaces..");
    }

    private static ArchRule validationPackagesDoNotDependOnInfrastructure(String... sourcePackages) {
        return noClasses()
                .that().resideInAnyPackage(sourcePackages)
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");
    }

    private static ArchRule validationCoreDoesNotDependOnJdbcOrJpa(String validationPackage) {
        return noClasses()
                .that().resideInAnyPackage(
                        validationPackage + ".domain..",
                        validationPackage + ".application..",
                        validationPackage + ".interfaces..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.jdbc..", "java.sql..", "javax.sql..",
                        "jakarta.persistence..", "javax.persistence..", "org.springframework.data.jpa..");
    }

    private static ArchRule validationCoreDoesNotDependOnForeignRepositoryAdapters(
            String validationPackage, String... foreignPackages) {
        return noClasses()
                .that().resideInAnyPackage(
                        validationPackage + ".application..", validationPackage + ".interfaces..")
                .should().dependOnClassesThat(
                        resideInAnyPackage(foreignPackages)
                                .and(simpleNameEndingWith("Repository")
                                        .or(simpleNameEndingWith("Store"))
                                        .or(simpleNameEndingWith("Adapter"))));
    }
}
