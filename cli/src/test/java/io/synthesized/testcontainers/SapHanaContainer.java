package io.synthesized.testcontainers;

import com.github.dockerjava.api.model.Ulimit;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Custom container for SAP HANA Express Edition.
 *
 * Based on Spring Batch's HANAJobRepositoryIntegrationTests implementation.
 * SAP HANA Express Edition image is only available for linux/amd64.
 *
 * @see <a href="https://hub.docker.com/r/saplabs/hanaexpress">SAP HANA Express Docker Image</a>
 * @see <a href="https://github.com/spring-projects/spring-batch">Spring Batch HANA Tests</a>
 */
@SuppressWarnings("unchecked")
public class SapHanaContainer extends JdbcDatabaseContainer<SapHanaContainer> {

    public static final String DEFAULT_IMAGE = "saplabs/hanaexpress:2.00.088.00.20251110.1";

    private static final int PORT = 39041;
    private static final String DRIVER_CLASS_NAME = "com.sap.db.jdbc.Driver";

    // SYSTEM user is needed to run init script that creates TESTUSER
    private static final String DEFAULT_USER = "SYSTEM";
    private static final String DEFAULT_PASSWORD = "HXEHana1";

    // Test user credentials - tests should use these after init script runs
    public static final String TEST_USER = "TESTUSER";
    public static final String TEST_PASSWORD = "TestUser123";

    public SapHanaContainer() {
        this(DockerImageName.parse(DEFAULT_IMAGE));
    }

    public SapHanaContainer(String imageName) {
        this(DockerImageName.parse(imageName));
    }

    public SapHanaContainer(DockerImageName dockerImageName) {
        super(dockerImageName);

        addExposedPort(PORT);

        withCreateContainerCmdModifier(cmd -> {
            if (cmd.getHostConfig() != null) {
                cmd.getHostConfig()
                        .withUlimits(List.of(new Ulimit("nofile", 1048576L, 1048576L)))
                        .withSysctls(Map.of(
                                "kernel.shmmax", "1073741824",
                                "net.ipv4.ip_local_port_range", "40000 60999"
                        ));
            }
        });

        withCommand("--master-password " + DEFAULT_PASSWORD + " --agree-to-sap-license");

        this.waitStrategy = new LogMessageWaitStrategy()
                .withRegEx(".*Startup finished!.*\\s")
                .withTimes(1)
                .withStartupTimeout(Duration.ofSeconds(1200));
    }

    @Override
    protected void configure() {
        if (!getEnvMap().containsKey("AGREE_TO_SAP_LICENSE")) {
            acceptLicense();
        }
    }

    public SapHanaContainer acceptLicense() {
        addEnv("AGREE_TO_SAP_LICENSE", "Y");
        return self();
    }

    @Override
    public Set<Integer> getLivenessCheckPortNumbers() {
        return Set.of(getMappedPort(PORT));
    }

    @Override
    protected void waitUntilContainerStarted() {
        waitStrategy.waitUntilReady(this);
    }

    @Override
    public String getDriverClassName() {
        return DRIVER_CLASS_NAME;
    }

    @Override
    public String getJdbcUrl() {
        return "jdbc:sap://" + getHost() + ":" + getMappedPort(PORT) + "/";
    }

    @Override
    public String getUsername() {
        return DEFAULT_USER;
    }

    @Override
    public String getPassword() {
        return DEFAULT_PASSWORD;
    }

    @Override
    public String getTestQueryString() {
        return "SELECT 1 FROM SYS.DUMMY";
    }
}