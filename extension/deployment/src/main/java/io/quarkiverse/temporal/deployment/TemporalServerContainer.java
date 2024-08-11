package io.quarkiverse.temporal.deployment;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class TemporalServerContainer extends GenericContainer<TemporalServerContainer> {


    public TemporalServerContainer(DockerImageName imageName) {
        super(imageName);
    }
}
