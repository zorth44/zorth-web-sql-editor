package com.bocsoft.sqleditor;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.bocsoft.sqleditor", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule commonDoesNotDependOnFeatures = noClasses()
        .that().resideInAPackage("..common..")
        .should().dependOnClassesThat().resideInAnyPackage("..auth..", "..datasource..");

    @ArchTest
    static final ArchRule authDoesNotDependOnDataSources = noClasses()
        .that().resideInAPackage("..auth..")
        .should().dependOnClassesThat().resideInAPackage("..datasource..");
}
