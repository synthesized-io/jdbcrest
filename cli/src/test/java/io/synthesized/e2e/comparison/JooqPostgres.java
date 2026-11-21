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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.jdbc.JdbcDatabaseDelegate;

import java.util.HashMap;
import java.util.Map;

public class JooqPostgres implements Backend {
    @Override
    public Endpoint start(Store store) {
        var postgres = postgres(store);
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", postgres.getJdbcUrl());
        properties.put("spring.datasource.username", postgres.getUsername());
        properties.put("spring.datasource.password", postgres.getPassword());
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("jdbcrest.database-type", "JOOQ_POSTGRES");
        return Backend.startApplication(store, properties, "public");
    }

    static PostgreSQLContainer<?> postgres(Store store) {
        var network = Backend.network(store);
        return store.computeIfAbsent(PostgreSQLContainer.class, key -> {
            var postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withNetwork(network).withNetworkAliases("db");
            try {
                postgres.start();
                ScriptUtils.runInitScript(new JdbcDatabaseDelegate(postgres, ""), "db/init.sql");
                return postgres;
            } catch (RuntimeException | Error failure) {
                try {
                    postgres.close();
                } catch (RuntimeException | Error closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        }, PostgreSQLContainer.class);
    }
}
