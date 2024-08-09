package fr.lavachequicode.temporal.plugin.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

class TemporalMockRequiredTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .assertException(ex -> {
                Assertions.assertEquals(ConfigurationException.class, ex.getClass());
                Assertions.assertEquals("Please add the quarkus-temporal-test extension to enable mocking", ex.getMessage());
            })
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.temporal.enable-mock: true\n"), "application.properties"));

    @Test
    public void testEnableMockWithoutTestExtension() {
        // should not be called, deployment exception should happen first:
        // it's illegal to set enable-mock without the test extension
        Assertions.fail();
    }

}
