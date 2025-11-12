package io.quarkiverse.temporal.it.dataconverter;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.DataConverter;

/**
 * Small probe to read the WorkflowClient's resolved options and return the DataConverter identity.
 */
@Path("/converter-probe")
public class ConverterProbeResource {

    @Inject
    WorkflowClient client;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String probe() {
        WorkflowClientOptions opts = client.getOptions();
        DataConverter dc = opts.getDataConverter();
        return dc == CustomMarkerDataConverter.INSTANCE ? "CDI" : (dc == null ? "NULL" : dc.getClass().getName());
    }
}