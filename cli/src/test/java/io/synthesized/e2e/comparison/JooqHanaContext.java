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

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.synthesized.App;
import io.synthesized.e2e.ComparisonTestExtension;
import io.synthesized.testcontainers.SapHanaContainer;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.jdbc.JdbcDatabaseDelegate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JooqHanaContext implements ComparisonContext {

    private static final String HANA = "hana";
    private static final String APP_HANA_PORT = "app_hana_port";

    @Override
    public String getDisplayName(int invocationIndex) {
        return "jooq_hana";
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(new ParameterResolver() {

            @Override
            public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
                    throws ParameterResolutionException {
                return parameterContext.getParameter().getType() == RequestSpecification.class;
            }

            @Override
            public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
                    throws ParameterResolutionException {
                ExtensionContext.Store store = extensionContext.getStore(ComparisonTestExtension.NAMESPACE);
                int port = (int) store.get(APP_HANA_PORT);
                if (parameterContext.getParameter().getType() == RequestSpecification.class) {
                    return RestAssured.given()
                            .baseUri("http://localhost/api/data/TESTUSER")
                            .port(port);
                } else throw new IllegalStateException();
            }
        });
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(ComparisonTestExtension.NAMESPACE);
        store.get(HANA, AutoCloseable.class).close();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(ComparisonTestExtension.NAMESPACE);
        final Network network = store.get(ComparisonTestExtension.NETWORK, Network.class);
        final JdbcDatabaseContainer<?> saphana = new SapHanaContainer()
                .withInitScript("db/createHanaTestUser.sql")
                .acceptLicense()
                .withNetwork(network).withNetworkAliases("hana");
        saphana.start();
        JdbcDatabaseDelegate hanaDelegate = new JdbcDatabaseDelegate(saphana, "");
        ScriptUtils.runInitScript(hanaDelegate, "db/inithana.sql");
        // Start Spring Boot app with SAP HANA
        Map<String, Object> hanaprops = new HashMap<>();
        hanaprops.put("spring.datasource.url", saphana.getJdbcUrl());
        hanaprops.put("spring.datasource.username", saphana.getUsername());
        hanaprops.put("spring.datasource.password", saphana.getPassword());
        hanaprops.put("jdbcrest.database-type", "JOOQ_HANA");
        hanaprops.put("server.port", 0); // random free port

        SpringApplication hanaApp = new SpringApplication(App.class);
        hanaApp.setDefaultProperties(hanaprops);
        ServletWebServerApplicationContext serverContext = (ServletWebServerApplicationContext) hanaApp.run();
        int hanaPort = serverContext.getWebServer().getPort();

        store.put(HANA, saphana);
        store.put(APP_HANA_PORT, hanaPort);
    }
}
