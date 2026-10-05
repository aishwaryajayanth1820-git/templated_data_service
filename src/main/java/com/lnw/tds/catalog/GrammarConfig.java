package com.lnw.tds.catalog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.lnw.tds.ddl.DdlGenerator;
import com.lnw.tds.ddl.MigrationPlanner;
import com.lnw.tds.ddl.SqlDialect;
import com.lnw.tds.grammar.RecordValidator;
import com.lnw.tds.grammar.SemanticValidator;
import com.lnw.tds.grammar.StructuralValidator;
import com.lnw.tds.grammar.TemplateParser;
import com.lnw.tds.jdbc.DbVendorResolver;

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
