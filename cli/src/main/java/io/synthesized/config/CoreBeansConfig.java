package io.synthesized.config;

import io.synthesized.jdbcrest.DataRetrieval;
import io.synthesized.jdbcrest.JooqQueryTranspiler;
import io.synthesized.jdbcrest.PostgreQueryTranspiler;
import io.synthesized.jdbcrest.QueryTranspiler;
import org.jooq.SQLDialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class CoreBeansConfig {

    @Bean
    public DataRetrieval dataRetrieval(
            DataSource dataSource,
            @Value("${jdbcrest.database-type:JOOQ_POSTGRES}") String databaseTypeProp
    ) {
        QueryTranspiler queryTranspiler =
                switch (databaseTypeProp.toUpperCase()) {
                    case "JOOQ_POSTGRES" -> new JooqQueryTranspiler(SQLDialect.POSTGRES);
                    case "JOOQ_HANA" -> new JooqQueryTranspiler(SQLDialect.valueOf("HANA"));
                    default -> new PostgreQueryTranspiler();
                };
        return new DataRetrieval(dataSource, queryTranspiler);
    }
}
