package io.synthesized.config;

import io.synthesized.jdbcrest.DataRetrieval;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class CoreBeansConfig {

    @Bean
    public DataRetrieval dataRetrieval(DataSource dataSource) {
        return new DataRetrieval(dataSource);
        }
}
