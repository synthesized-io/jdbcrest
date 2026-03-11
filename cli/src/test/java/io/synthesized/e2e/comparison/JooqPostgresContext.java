package io.synthesized.e2e.comparison;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.synthesized.App;
import io.synthesized.e2e.ComparisonTestExtension;
import org.junit.jupiter.api.extension.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.jdbc.JdbcDatabaseDelegate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JooqPostgresContext implements ComparisonContext {
    static final String POSTGRES = "postgres";
    private static final String APP_PORT = "app_port";

    @Override
    public String getDisplayName(int invocationIndex) {
        return "jooq_postgres";
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(new ParameterResolver() {

            @Override
            public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
                return parameterContext.getParameter().getType() == RequestSpecification.class;
            }

            @Override
            public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
                ExtensionContext.Store store = extensionContext.getStore(ComparisonTestExtension.NAMESPACE);
                int port = store.get(APP_PORT, Integer.class);
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
        final Network network = store.get(ComparisonTestExtension.NETWORK, Network.class);
        final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withNetwork(network).withNetworkAliases("db");

        postgres.start();
        JdbcDatabaseDelegate delegate = new JdbcDatabaseDelegate(postgres, "");
        ScriptUtils.runInitScript(delegate, "db/init.sql");

        // Start Spring Boot app with Testcontainers DB credentials and random port
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", postgres.getJdbcUrl());
        props.put("spring.datasource.username", postgres.getUsername());
        props.put("spring.datasource.password", postgres.getPassword());
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        props.put("jdbcrest.database-type", "JOOQ_POSTGRES");
        props.put("server.port", 0); // random free port

        SpringApplication app = new SpringApplication(App.class);
        app.setDefaultProperties(props);

        ServletWebServerApplicationContext serverContext = (ServletWebServerApplicationContext) app.run();
        int port = serverContext.getWebServer().getPort();
        store.put(APP_PORT, port);
        store.put(POSTGRES, postgres);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(ComparisonTestExtension.NAMESPACE);
        store.get(POSTGRES, AutoCloseable.class).close();
    }
}
