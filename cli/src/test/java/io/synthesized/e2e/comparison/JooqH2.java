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
package io.synthesized.e2e.comparison;

import org.junit.jupiter.api.extension.ExtensionContext.Store;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Uses an embedded H2 database in PostgreSQL compatibility mode. */
public class JooqH2 implements Backend {
    private static final String USERNAME = "sa";
    private static final String PASSWORD = "";

    @Override
    public Endpoint start(Store store) throws SQLException {
        String jdbcUrl = "jdbc:h2:mem:jdbcrest_e2e_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        var keepAlive = Backend.keep(store, DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD));
        try (var statement = keepAlive.createStatement()) {
            statement.execute("RUNSCRIPT FROM 'classpath:db/inith2.sql'");
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", jdbcUrl);
        properties.put("spring.datasource.username", USERNAME);
        properties.put("spring.datasource.password", PASSWORD);
        properties.put("spring.datasource.driver-class-name", "org.h2.Driver");
        properties.put("jdbcrest.database-type", "JOOQ_H2");
        return Backend.startApplication(store, properties, "public");
    }
}
