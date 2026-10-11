package io.github.aishwaryajayanth1820.tds;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Package rules from application design §2. */
@AnalyzeClasses(packages = "io.github.aishwaryajayanth1820.tds", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule grammarIsPure = noClasses().that().resideInAPackage("io.github.aishwaryajayanth1820.tds.grammar..")
            .should().dependOnClassesThat(resideInAnyPackage("org.springframework..", "jakarta..")
                    .or(resideInAPackage("io.github.aishwaryajayanth1820.tds..").and(not(resideInAPackage("io.github.aishwaryajayanth1820.tds.grammar..")))))
            .allowEmptyShould(true)
            .because("the grammar must stay framework-free and unit-testable (application design §2)");

    @ArchTest
    static final ArchRule jdbcIsFoundation = noClasses().that().resideInAPackage("io.github.aishwaryajayanth1820.tds.jdbc..")
            .should().dependOnClassesThat(resideInAPackage("io.github.aishwaryajayanth1820.tds..").and(not(resideInAPackage("io.github.aishwaryajayanth1820.tds.jdbc.."))));

    @ArchTest
    static final ArchRule noPackageCycles = slices().matching("io.github.aishwaryajayanth1820.tds.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule controllersInApiPackages = classes().that().areAnnotatedWith(RestController.class)
            .should().resideInAnyPackage("io.github.aishwaryajayanth1820.tds.security..", "io.github.aishwaryajayanth1820.tds.data..", "io.github.aishwaryajayanth1820.tds.meta..",
                    "io.github.aishwaryajayanth1820.tds.admin..", "io.github.aishwaryajayanth1820.tds.script..");
}
