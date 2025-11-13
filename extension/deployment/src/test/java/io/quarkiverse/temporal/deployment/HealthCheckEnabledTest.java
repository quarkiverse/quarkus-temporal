package io.quarkiverse.temporal.deployment;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class HealthCheckEnabledTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.temporal.start-workers: false\n" + "quarkus.temporal.health.enabled: true\n"),
                            "application.properties"));

    @Test
    @Disabled
    public void testDataSourceHealthCheckExclusion() {
        RestAssured.when().get("/q/health/ready")
                .then()
                .body("status", equalTo("DOWN")) // Verifies that the status at the root level is "DOWN"
                .body("checks", hasSize(1)) // Verifies that there is exactly one check in the "checks" array
                .body("checks[0].name", equalTo("Temporal")) // Verifies that the name of the first check is "Temporal"
                .body("checks[0].status", equalTo("DOWN")) // Verifies that the status of the first check is "DOWN"
                .body("checks[0].data.'127.0.0.1:7233'", equalTo("DOWN"));
    }
}