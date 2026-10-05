package com.lnw.tds;

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
@AnalyzeClasses(packages = "com.lnw.tds", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule grammarIsPure = noClasses().that().resideInAPackage("com.lnw.tds.grammar..")
            .should().dependOnClassesThat(resideInAnyPackage("org.springframework..", "jakarta..")
                    .or(resideInAPackage("com.lnw.tds..").and(not(resideInAPackage("com.lnw.tds.grammar..")))))
            .allowEmptyShould(true)
            .because("the grammar must stay framework-free and unit-testable (application design §2)");

    @ArchTest
    static final ArchRule jdbcIsFoundation = noClasses().that().resideInAPackage("com.lnw.tds.jdbc..")
            .should().dependOnClassesThat(resideInAPackage("com.lnw.tds..").and(not(resideInAPackage("com.lnw.tds.jdbc.."))));

    @ArchTest
    static final ArchRule noPackageCycles = slices().matching("com.lnw.tds.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule controllersInApiPackages = classes().that().areAnnotatedWith(RestController.class)
            .should().resideInAnyPackage("com.lnw.tds.security..", "com.lnw.tds.data..", "com.lnw.tds.meta..",
                    "com.lnw.tds.admin..", "com.lnw.tds.script..");
}
