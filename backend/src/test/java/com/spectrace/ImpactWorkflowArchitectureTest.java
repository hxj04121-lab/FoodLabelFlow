package com.spectrace;

import com.spectrace.archfixture.badimpact.application.BadImpactService;
import com.spectrace.archfixture.badworkflow.infrastructure.WorkflowAdapter;
import com.spectrace.audit.application.port.ImpactAuditEventPort;
import com.spectrace.impact.application.ImpactAnalysisApplicationService;
import com.spectrace.impact.application.port.ImpactAnalysisRunRepository;
import com.spectrace.impact.application.port.ImpactFindingRepository;
import com.spectrace.impact.application.port.ReviewTaskLinkageRepository;
import com.spectrace.impact.domain.ImpactAnalysisRun;
import com.spectrace.impact.domain.ImpactFinding;
import com.spectrace.impact.domain.ReviewTaskLinkage;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(packages = "com.spectrace", importOptions = ImportOption.DoNotIncludeTests.class)
class ImpactWorkflowArchitectureTest {
    private static final String IMPACT = "com.spectrace.impact";
    private static final String WORKFLOW = "com.spectrace.workflow";

    @ArchTest
    static final ArchRule impact_domain_is_pure = noClasses()
            .that().resideInAnyPackage(IMPACT + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "org.springframework..", "java.sql..", "javax.sql..",
                    "jakarta.persistence..", "javax.persistence..", "org.springframework.data.jpa..");

    @ArchTest
    static final ArchRule impact_application_uses_ports_instead_of_infrastructure =
            doesNotDependOnInfrastructure(IMPACT + ".application..");

    @ArchTest
    static final ArchRule impact_interfaces_use_application_boundaries =
            doesNotDependOnInfrastructure(IMPACT + ".interfaces..");

    @ArchTest
    static final ArchRule impact_domain_application_and_interfaces_do_not_depend_on_jdbc_or_jpa =
            coreDoesNotDependOnJdbcOrJpa(IMPACT);

    @ArchTest
    static final ArchRule impact_infrastructure_does_not_depend_on_foreign_infrastructure =
            infrastructureDoesNotDependOnForeignInfrastructure(IMPACT);

    @ArchTest
    static final ArchRule workflow_domain_is_pure = noClasses()
            .that().resideInAnyPackage(WORKFLOW + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "org.springframework..", "java.sql..", "javax.sql..",
                    "jakarta.persistence..", "javax.persistence..", "org.springframework.data.jpa..");

    @ArchTest
    static final ArchRule workflow_application_uses_ports_instead_of_infrastructure =
            doesNotDependOnInfrastructure(WORKFLOW + ".application..");

    @ArchTest
    static final ArchRule workflow_interfaces_use_application_boundaries =
            doesNotDependOnInfrastructure(WORKFLOW + ".interfaces..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule workflow_domain_application_and_interfaces_do_not_depend_on_jdbc_or_jpa =
            coreDoesNotDependOnJdbcOrJpa(WORKFLOW);

    @ArchTest
    static final ArchRule workflow_infrastructure_does_not_depend_on_foreign_infrastructure =
            infrastructureDoesNotDependOnForeignInfrastructure(WORKFLOW);

    @Test
    void architecture_rules_reject_a_foreign_infrastructure_shortcut() {
        JavaClasses fixture = new ClassFileImporter().importClasses(BadImpactService.class, WorkflowAdapter.class);

        assertThat(doesNotDependOnInfrastructure("com.spectrace.archfixture.badimpact.application..")
                .evaluate(fixture).hasViolation()).isTrue();
    }

    @Test
    void real_impact_service_can_depend_on_application_ports() {
        JavaClasses productionTypes = new ClassFileImporter().importClasses(
                ImpactAnalysisApplicationService.class,
                ImpactAnalysisRunRepository.class,
                ImpactFindingRepository.class,
                ReviewTaskLinkageRepository.class,
                ImpactAuditEventPort.class,
                ImpactAnalysisRun.class,
                ImpactFinding.class,
                ReviewTaskLinkage.class);

        assertThat(doesNotDependOnInfrastructure(IMPACT + ".application..")
                .evaluate(productionTypes).hasViolation()).isFalse();
        assertThat(coreDoesNotDependOnJdbcOrJpa(IMPACT)
                .evaluate(productionTypes).hasViolation()).isFalse();
    }

    private static ArchRule doesNotDependOnInfrastructure(String sourcePackage) {
        return noClasses()
                .that().resideInAnyPackage(sourcePackage)
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");
    }

    private static ArchRule coreDoesNotDependOnJdbcOrJpa(String modulePackage) {
        return noClasses()
                .that().resideInAnyPackage(
                        modulePackage + ".domain..",
                        modulePackage + ".application..",
                        modulePackage + ".interfaces..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.jdbc..", "java.sql..", "javax.sql..",
                        "jakarta.persistence..", "javax.persistence..", "org.springframework.data.jpa..");
    }

    private static ArchRule infrastructureDoesNotDependOnForeignInfrastructure(String ownerPackage) {
        return noClasses()
                .that().resideInAnyPackage(ownerPackage + ".infrastructure..")
                .should().dependOnClassesThat(foreignInfrastructure(ownerPackage));
    }

    private static DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass> foreignInfrastructure(
            String ownerPackage) {
        String ownInfrastructure = ownerPackage + ".infrastructure.";
        return new DescribedPredicate<>("reside in a foreign infrastructure package") {
            @Override
            public boolean test(com.tngtech.archunit.core.domain.JavaClass javaClass) {
                String packageName = javaClass.getPackageName();
                return packageName.startsWith("com.spectrace.")
                        && packageName.contains(".infrastructure.")
                        && !packageName.startsWith(ownInfrastructure);
            }
        };
    }
}
