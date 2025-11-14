package io.quarkiverse.temporal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import io.quarkus.arc.SyntheticCreationalContext;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.interceptors.WorkflowClientInterceptor;

public final class WorkflowClientOptionsSupport {

    private WorkflowClientOptionsSupport() {
    }

    public static WorkflowClientOptions buildFromContext(
            SyntheticCreationalContext<?> context,
            String namespace,
            Optional<String> identity) {

        WorkflowClientOptions.Builder builder = WorkflowClientOptions.newBuilder()
                .setNamespace(namespace);

        identity.ifPresent(builder::setIdentity);

        // obtain the data converter from CDI at runtime if available
        Instance<DataConverter> dataConverterInstance = context.getInjectedReference(new TypeLiteral<>() {
        }, Any.Literal.INSTANCE);

        DataConverter dataConverter = dataConverterInstance.isResolvable()
                ? dataConverterInstance.get()
                : null;

        if (dataConverter != null) {
            builder.setDataConverter(dataConverter);
        }

        // discover interceptors
        Instance<WorkflowClientInterceptor> interceptorInstance = context.getInjectedReference(new TypeLiteral<>() {
        }, Any.Literal.INSTANCE);

        List<WorkflowClientInterceptor> interceptors = interceptorInstance.stream()
                .collect(Collectors.toCollection(ArrayList::new));

        if (!interceptors.isEmpty()) {
            builder.setInterceptors(interceptors.toArray(new WorkflowClientInterceptor[0]));
        }

        // discover propagators
        Instance<ContextPropagator> contextPropagatorInstance = context.getInjectedReference(new TypeLiteral<>() {
        }, Any.Literal.INSTANCE);

        List<ContextPropagator> propagators = contextPropagatorInstance.stream()
                .collect(Collectors.toCollection(ArrayList::new));
        if (!propagators.isEmpty()) {
            builder.setContextPropagators(propagators);
        }

        return builder.validateAndBuildWithDefaults();
    }
}
