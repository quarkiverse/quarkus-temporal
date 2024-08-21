package io.quarkiverse.temporal.deployment.devui;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

// import io.quarkus.devservices.common.ConfigureUtil;

public class TemporalContainer extends GenericContainer<TemporalContainer> {

    private static final Integer SERVER_EXPOSED_PORT = 7233;
    private static final Integer UI_EXPOSED_PORT = 8233;

    private final TemporalDevserviceConfig config;
    private String serviceName;
    // private String hostname;

    public TemporalContainer(DockerImageName dockerImageName, TemporalDevserviceConfig config) {
        super(dockerImageName);
        this.config = config;
        this.serviceName = "temporal";
    }

    @Override
    protected void configure() {
        super.configure();

        withCreateContainerCmdModifier(cmd -> {
            cmd.withEntrypoint("/usr/local/bin/temporal");
            cmd.withCmd("server", "start-dev", "--ip", "0.0.0.0");
        });

        withExposedPorts(SERVER_EXPOSED_PORT, UI_EXPOSED_PORT);

        withLabel("quarkus-devservice-temporal", serviceName);

        withReuse(config.reuse());

        // hostname = ConfigureUtil.configureSharedNetwork(this, "temporal-" + serviceName);
    }

}
