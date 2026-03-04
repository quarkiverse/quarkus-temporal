package io.quarkiverse.temporal.deployment;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Verifies Temporal readiness health check when health is enabled and Temporal is
 * unreachable.
 *
 * The test forces an invalid Temporal target so expected status is deterministic
 * and independent from local environment state.
 */
public class HealthCheckEnabledTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.temporal.start-workers: false\n" +
                                    // Explicitly enable Temporal readiness check.
                                            "quarkus.temporal.health.enabled: true\n" +
                                            // Force deterministic DOWN status regardless of local Temporal availability.
                                            "quarkus.temporal.connection.target: 127.0.0.1:1\n"),
                            "application.properties"));

    @Test
    public void testDataSourceHealthCheckExclusion() {
        RestAssured.when().get("/q/health/ready")
                .then()
                // Overall readiness must be DOWN because Temporal check is DOWN.
                .body("status", equalTo("DOWN"))
                // Exactly one readiness check is expected in this test archive.
                .body("checks", hasSize(1))
                // Validate Temporal check identity and status.
                .body("checks[0].name", equalTo("Temporal"))
                .body("checks[0].status", equalTo("DOWN"))
                // Validate target-specific check data.
                .body("checks[0].data.'127.0.0.1:1'", equalTo("DOWN"));
    }
}
