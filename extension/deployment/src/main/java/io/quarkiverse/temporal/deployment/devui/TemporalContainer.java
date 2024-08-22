package io.quarkiverse.temporal.deployment.devui;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

// import io.quarkus.devservices.common.ConfigureUtil;

public class TemporalContainer extends GenericContainer<TemporalContainer> {

    private static final Integer SERVER_EXPOSED_PORT = 7233;
    private static final Integer UI_EXPOSED_PORT = 8233;

    private final TemporalDevserviceConfig config;
    private String serviceName;
    private String label;
    private String path;
    // private String hostname;

    public TemporalContainer(DockerImageName dockerImageName, TemporalDevserviceConfig config, String path, String label) {
        super(dockerImageName);
        this.config = config;
        this.label = label;
        this.path = path;
        this.serviceName = "temporal";
    }

    @Override
    protected void configure() {
        super.configure();

        withCreateContainerCmdModifier(cmd -> {
            cmd.withEntrypoint("/usr/local/bin/temporal");
            cmd.withCmd("server", "start-dev", "--ip", "0.0.0.0", "--ui-public-path", path);
        });

        withExposedPorts(SERVER_EXPOSED_PORT, UI_EXPOSED_PORT);

        withLabel(label, serviceName);

        withReuse(config.reuse());

        // hostname = ConfigureUtil.configureSharedNetwork(this, "temporal-" + serviceName);
    }

    public String getUiUrl() {
        return "http://" + getHost() + ":" + getMappedPort(UI_EXPOSED_PORT) + path;
    }
}
