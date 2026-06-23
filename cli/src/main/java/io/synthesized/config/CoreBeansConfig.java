/*
 * Copyright 2026 Synthesized Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
                    case "JOOQ_H2" -> new JooqQueryTranspiler(SQLDialect.H2);
                    case "JOOQ_HANA" -> new JooqQueryTranspiler(SQLDialect.valueOf("HANA"));
                    default -> new PostgreQueryTranspiler();
                };
        return new DataRetrieval(dataSource, queryTranspiler);
    }
}
