package io.quarkiverse.temporal;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.spi.CreationalContext;

import org.jboss.logging.Logger;

import com.uber.m3.tally.Scope;

import io.quarkus.arc.BeanDestroyer;
import io.temporal.serviceclient.WorkflowServiceStubs;

/**
 * Shuts down the {@link WorkflowServiceStubs} bean when the application stops, whether or not workers were started.
 * The stubs only shut down a gRPC channel they created themselves, so a Quarkus managed channel is left to Quarkus.
 * The metrics scope is created by the extension and closed here as well.
 */
public class WorkflowServiceStubsDestroyer implements BeanDestroyer<WorkflowServiceStubs> {

    private static final Logger log = Logger.getLogger(WorkflowServiceStubsDestroyer.class);

    static final long TERMINATION_TIMEOUT_SECONDS = 10;

    @Override
    public void destroy(WorkflowServiceStubs stubs, CreationalContext<WorkflowServiceStubs> creationalContext,
            Map<String, Object> params) {
        try {
            stubs.shutdown();
            if (!stubs.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warnf("Temporal service stubs did not terminate within %d s, forcing shutdown",
                        TERMINATION_TIMEOUT_SECONDS);
                stubs.shutdownNow();
            }
        } catch (RuntimeException e) {
            log.warn("Failed to shut down Temporal service stubs", e);
        } finally {
            closeMetricsScope(stubs);
        }
    }

    private void closeMetricsScope(WorkflowServiceStubs stubs) {
        Scope scope = stubs.getOptions().getMetricsScope();
        if (scope == null) {
            return;
        }
        try {
            scope.close();
        } catch (Exception e) {
            log.warn("Failed to close Temporal metrics scope", e);
        }
    }
}
