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
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comparison context backed by an embedded H2 database running in-process. Unlike the other
 * contexts this needs no Testcontainers container: the database lives entirely inside the JVM.
 *
 * <p>H2 runs in PostgreSQL compatibility mode with {@code DATABASE_TO_LOWER=TRUE} so that unquoted
 * identifiers fold to lower case and sit in the lower-case {@code public} schema, matching the
 * quoted, lower-case identifiers jOOQ emits for {@link org.jooq.SQLDialect#H2}.
 */
public class JooqH2Context implements ComparisonContext {
    private static final String H2_CONNECTION = "h2_connection";
    private static final String APP_H2_PORT = "app_h2_port";

    private static final String JDBC_URL =
            "jdbc:h2:mem:jdbcrest_e2e;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static final String USERNAME = "sa";
    private static final String PASSWORD = "";

    @Override
    public String getDisplayName(int invocationIndex) {
        return "jooq_h2";
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
                int port = store.get(APP_H2_PORT, Integer.class);
                if (parameterContext.getParameter().getType() == RequestSpecification.class) {
                    return RestAssured.given()
                            .baseUri("http://localhost/api/data/public")
                            .port(port);
                } else throw new IllegalStateException();
            }
        });
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(ComparisonTestExtension.NAMESPACE);

        // Keep one connection open for the lifetime of the test so the in-memory database
        // (and its schema and data) survives between the connections opened by the app's pool.
        Connection keepAlive = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
        try (Statement stmt = keepAlive.createStatement()) {
            stmt.execute("RUNSCRIPT FROM 'classpath:db/inith2.sql'");
        }

        // Start Spring Boot app against the embedded H2 database on a random free port
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", JDBC_URL);
        props.put("spring.datasource.username", USERNAME);
        props.put("spring.datasource.password", PASSWORD);
        props.put("spring.datasource.driver-class-name", "org.h2.Driver");
        props.put("jdbcrest.database-type", "JOOQ_H2");
        props.put("server.port", 0); // random free port

        SpringApplication app = new SpringApplication(App.class);
        app.setDefaultProperties(props);

        ServletWebServerApplicationContext serverContext = (ServletWebServerApplicationContext) app.run();
        int port = serverContext.getWebServer().getPort();
        store.put(APP_H2_PORT, port);
        store.put(H2_CONNECTION, keepAlive);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(ComparisonTestExtension.NAMESPACE);
        store.get(H2_CONNECTION, AutoCloseable.class).close();
    }
}
