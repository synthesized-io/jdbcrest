package io.synthesized.e2e.comparison;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.synthesized.e2e.ComparisonTestExtension;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.List;

public class PostgrestContext implements ComparisonContext {
    public static final String POSTGREST = "postgrest";
    public static final String POSTGREST_PORT = "portgrest_port";

    @Override
    public String getDisplayName(int invocationIndex) {
        return POSTGREST;
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
                if (parameterContext.getParameter().getType() == RequestSpecification.class) {
                    ExtensionContext.Store store = extensionContext.getStore(ComparisonTestExtension.NAMESPACE);
                    int port = store.get(POSTGREST_PORT, Integer.class);
                    return RestAssured.given()
                            .baseUri("http://localhost")
                            .port(port);
                } else throw new IllegalStateException();
            }
        });
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(ComparisonTestExtension.NAMESPACE);
        store.get(POSTGREST, AutoCloseable.class).close();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(ComparisonTestExtension.NAMESPACE);
        final Network network = store.get(ComparisonTestExtension.NETWORK, Network.class);
        final GenericContainer<?> postgrest = new GenericContainer<>("postgrest/postgrest:v12.2.3")
                .withExposedPorts(3000)
                .withNetwork(network)
                .waitingFor(Wait.forLogMessage(".*Listening on.*\\n", 1));

        // Configure and start PostgREST after DB is ready
        // network alias reachable from the PostgREST container
        PostgreSQLContainer<?> postgres = store.get(JooqPostgresContext.POSTGRES, PostgreSQLContainer.class);
        String dbUri = String.format("postgres://%s:%s@%s:%d/%s",
                postgres.getUsername(),
                postgres.getPassword(), "db", 5432, postgres.getDatabaseName());
        postgrest.addEnv("PGRST_DB_URI", dbUri);
        postgrest.addEnv("PGRST_DB_ANON_ROLE", "web_anon");
        postgrest.addEnv("PGRST_DB_SCHEMAS", "public");
        postgrest.start();
        store.put(POSTGREST, postgrest);
        store.put(POSTGREST_PORT, postgrest.getFirstMappedPort());
    }
}
