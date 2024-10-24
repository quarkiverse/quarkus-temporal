package io.quarkiverse.temporal.deployment.devui;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class TemporalContainer extends GenericContainer<TemporalContainer> {

    private static final Integer SERVER_EXPOSED_PORT = 7233;
    private static final Integer UI_EXPOSED_PORT = 8233;
    private final String path;

    public TemporalContainer(DockerImageName dockerImageName, String path, Boolean reuse) {
        super(dockerImageName);
        this.path = path;

        withCreateContainerCmdModifier(cmd -> {
            cmd.withEntrypoint("/usr/local/bin/temporal");
            cmd.withCmd(
                    "server", "start-dev",
                    "--ip", "0.0.0.0",
                    "--port", SERVER_EXPOSED_PORT.toString(),
                    "--ui-public-path", path);
        })
                .withExposedPorts(SERVER_EXPOSED_PORT, UI_EXPOSED_PORT)
                .withReuse(reuse);
    }

    public TemporalContainer(String dockerImageName, String path, Boolean reuse) {
        this(DockerImageName.parse(dockerImageName), path, reuse);
    }

    public String getUiUrl() {
        return "http://" + getHost() + ":" + getMappedPort(UI_EXPOSED_PORT) + path;
    }

    public String getServerUrl() {
        return getHost() + ":" + getMappedPort(SERVER_EXPOSED_PORT);
    }
}
