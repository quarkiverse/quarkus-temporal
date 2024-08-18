package io.quarkiverse.temporal.deployment;

import java.time.OffsetDateTime;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.config.NamedSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.config.SimpleActivity;
import io.quarkus.info.GitInfo;
import io.quarkus.test.Mock;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;

public class WorkerNamedDefaultConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(Producers.class)
                    .addClass(SimpleActivity.class)
                    .addClass(NamedSimpleActivityImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    public void testNamedConfiguration() throws IllegalAccessException {
        // queue name default to worker name
        Worker worker = factory.getWorker("namedWorker");
        Assertions.assertNotNull(worker);
        // worker config is not visible;
        WorkerOptions options = (WorkerOptions) FieldUtils.readField(worker, "options", true);
        Assertions.assertEquals("a0c88522ca7aa74f5e3a64e6adcef35c27af16ab", options.getBuildId());
    }

    public static class Producers {
        @Produces
        @Mock
        GitInfo mockGitInfo() {

            return new GitInfo() {
                @Override
                public String branch() {
                    return "main";
                }

                @Override
                public String latestCommitId() {
                    return "a0c88522ca7aa74f5e3a64e6adcef35c27af16ab";
                }

                @Override
                public OffsetDateTime commitTime() {
                    return null;
                }
            };
        }
    }
}
