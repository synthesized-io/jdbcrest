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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class Postgrest implements Backend {
    @Override
    public Endpoint start(Store store) {
        var postgres = JooqPostgres.postgres(store);
        var postgrest = Backend.keep(store, new GenericContainer<>("postgrest/postgrest:v12.2.3")
                .withExposedPorts(3000)
                .withNetwork(Backend.network(store))
                .waitingFor(Wait.forHttp("/").forPort(3000).forStatusCode(200)));

        String dbUri = String.format("postgres://%s:%s@%s:%d/%s",
                postgres.getUsername(), postgres.getPassword(), "db", 5432, postgres.getDatabaseName());
        postgrest.addEnv("PGRST_DB_URI", dbUri);
        postgrest.addEnv("PGRST_DB_ANON_ROLE", "web_anon");
        postgrest.addEnv("PGRST_DB_SCHEMAS", "public");
        postgrest.addEnv("PGRST_DB_AGGREGATES_ENABLED", "true");
        postgrest.start();
        return new Endpoint("http://" + postgrest.getHost(), postgrest.getFirstMappedPort());
    }
}
