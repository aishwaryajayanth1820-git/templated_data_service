package io.github.aishwaryajayanth1820.tds.catalog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.aishwaryajayanth1820.tds.ddl.DdlGenerator;
import io.github.aishwaryajayanth1820.tds.ddl.MigrationPlanner;
import io.github.aishwaryajayanth1820.tds.ddl.SqlDialect;
import io.github.aishwaryajayanth1820.tds.grammar.RecordValidator;
import io.github.aishwaryajayanth1820.tds.grammar.SemanticValidator;
import io.github.aishwaryajayanth1820.tds.grammar.StructuralValidator;
import io.github.aishwaryajayanth1820.tds.grammar.TemplateParser;
import io.github.aishwaryajayanth1820.tds.jdbc.DbVendorResolver;

import tools.jackson.databind.json.JsonMapper;

/** Spring wiring for the framework-free grammar and DDL classes. */
@Configuration(proxyBeanMethods = false)
public class GrammarConfig {

    @Bean
    StructuralValidator structuralValidator() {
        return new StructuralValidator();
    }

    @Bean
    TemplateParser templateParser(JsonMapper mapper, StructuralValidator structural) {
        return new TemplateParser(mapper, structural);
    }

    @Bean
    SemanticValidator semanticValidator() {
        return new SemanticValidator();
    }

    @Bean
    RecordValidator recordValidator() {
        return new RecordValidator();
    }

    @Bean
    SqlDialect sqlDialect(DbVendorResolver vendor) {
        return SqlDialect.of(vendor.current());
    }

    @Bean
    DdlGenerator ddlGenerator(SqlDialect dialect) {
        return new DdlGenerator(dialect);
    }

    @Bean
    MigrationPlanner migrationPlanner(DdlGenerator ddl) {
        return new MigrationPlanner(ddl);
    }
}
