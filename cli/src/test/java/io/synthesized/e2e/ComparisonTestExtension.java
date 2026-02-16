package io.synthesized.e2e;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.synthesized.App;
import org.junit.jupiter.api.extension.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.jdbc.JdbcDatabaseDelegate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ComparisonTestExtension implements TestTemplateInvocationContextProvider, BeforeAllCallback, AfterAllCallback {
    private final static ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ComparisonTestExtension.class);
    public static final String APP_PORT = "app_port";
    public static final String POSTGREST_PORT  = "portgrest_port";
    public static final String POSTGRES = "postgres";
    public static final String POSTGREST = "postgrest";

    @Override
    public void beforeAll(ExtensionContext context) {
        final Network network = Network.newNetwork();
        final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withNetwork(network).withNetworkAliases("db");
        final GenericContainer<?> postgrest = new GenericContainer<>("postgrest/postgrest:v12.2.3")
                .withExposedPorts(3000)
                .withNetwork(network)
                .waitingFor(Wait.forLogMessage(".*Listening on.*\\n", 1));

        // Initialize DB schema/data
        postgres.start();
        JdbcDatabaseDelegate delegate = new JdbcDatabaseDelegate(postgres, "");
        ScriptUtils.runInitScript(delegate, "db/init.sql");


        // Start Spring Boot app with Testcontainers DB credentials and random port
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", postgres.getJdbcUrl());
        props.put("spring.datasource.username", postgres.getUsername());
        props.put("spring.datasource.password", postgres.getPassword());
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        props.put("server.port", 0); // random free port

        SpringApplication app = new SpringApplication(App.class);
        app.setDefaultProperties(props);

        ServletWebServerApplicationContext serverContext = (ServletWebServerApplicationContext) app.run();

        int port = serverContext.getWebServer().getPort();


        // Configure and start PostgREST after DB is ready
        // network alias reachable from the PostgREST container
        String dbUri = String.format("postgres://%s:%s@%s:%d/%s",
                postgres.getUsername(),
                postgres.getPassword(), "db", 5432, postgres.getDatabaseName());
        postgrest.addEnv("PGRST_DB_URI", dbUri);
        postgrest.addEnv("PGRST_DB_ANON_ROLE", "web_anon");
        postgrest.addEnv("PGRST_DB_SCHEMAS", "public");
        postgrest.start();
        //Set up storage
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put(APP_PORT, port);
        store.put(POSTGRES, postgres);
        store.put(POSTGREST, postgrest);
        store.put(POSTGREST_PORT, postgrest.getFirstMappedPort());
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        ((AutoCloseable) store.get(POSTGRES)).close();
        ((AutoCloseable) store.get(POSTGREST)).close();
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return true;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        return Stream.of(new PostgrestContext(), new JDBCRestContext());
    }


    private static class PostgrestContext implements TestTemplateInvocationContext {

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
                        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
                        int port = (int) store.get(POSTGREST_PORT);
                        return RestAssured.given()
                                .baseUri("http://localhost")
                                .port(port);
                    } else throw new IllegalStateException();
                }
            });
        }
    }

    private static class JDBCRestContext implements TestTemplateInvocationContext {
        @Override
        public String getDisplayName(int invocationIndex) {
            return "jdbcrest";
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
                    ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
                    int port = (int) store.get(APP_PORT);
                    if (parameterContext.getParameter().getType() == RequestSpecification.class) {
                        return RestAssured.given()
                                .baseUri("http://localhost/api/data/public")
                                .port(port);
                    } else throw new IllegalStateException();
                }
            });
        }
    }
}
