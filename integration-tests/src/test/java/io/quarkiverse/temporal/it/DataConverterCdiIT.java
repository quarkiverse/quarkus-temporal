package io.quarkiverse.temporal.it;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

/**
 * Ensures the DataConverter used by WorkflowClient comes from CDI.
 */
@QuarkusTest
public class DataConverterCdiIT {

    @Test
    void shouldUseCdiProvidedDataConverter() {
        String result = RestAssured.get("/converter-probe")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        Assertions.assertEquals("CDI", result);
    }
}
