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

import io.synthesized.testcontainers.SapHanaContainer;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.jdbc.JdbcDatabaseDelegate;

import java.util.HashMap;
import java.util.Map;

public class JooqHana implements Backend {
    @Override
    public Endpoint start(Store store) {
        var hana = Backend.keep(store, new SapHanaContainer()
                .withInitScript("db/createHanaTestUser.sql")
                .acceptLicense()
                .withNetwork(Backend.network(store)).withNetworkAliases("hana"));
        hana.start();
        ScriptUtils.runInitScript(new JdbcDatabaseDelegate(hana, ""), "db/inithana.sql");

        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", hana.getJdbcUrl());
        properties.put("spring.datasource.username", hana.getUsername());
        properties.put("spring.datasource.password", hana.getPassword());
        properties.put("jdbcrest.database-type", "JOOQ_HANA");
        return Backend.startApplication(store, properties, "TESTUSER");
    }
}
