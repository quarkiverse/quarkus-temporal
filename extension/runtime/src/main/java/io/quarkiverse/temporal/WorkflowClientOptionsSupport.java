package io.quarkiverse.temporal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
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

        Instance<DataConverter> dataConverterInstance = context.getInjectedReference(new TypeLiteral<>() {
        }, Any.Literal.INSTANCE);
        Instance<WorkflowClientInterceptor> interceptorInstance = context.getInjectedReference(new TypeLiteral<>() {
        }, Any.Literal.INSTANCE);
        Instance<ContextPropagator> contextPropagatorInstance = context.getInjectedReference(new TypeLiteral<>() {
        }, Any.Literal.INSTANCE);

        return build(namespace, identity, dataConverterInstance, interceptorInstance, contextPropagatorInstance);
    }

    /**
     * Same result as {@link #buildFromContext}, but resolvable outside of synthetic bean
     * creation (e.g. from a JUnit test-lifecycle callback), using {@link CDI#current()} directly.
     */
    public static WorkflowClientOptions buildFromCurrentCdi(String namespace, Optional<String> identity) {
        Instance<DataConverter> dataConverterInstance = CDI.current().select(DataConverter.class, Any.Literal.INSTANCE);
        Instance<WorkflowClientInterceptor> interceptorInstance = CDI.current().select(WorkflowClientInterceptor.class,
                Any.Literal.INSTANCE);
        Instance<ContextPropagator> contextPropagatorInstance = CDI.current().select(ContextPropagator.class,
                Any.Literal.INSTANCE);

        return build(namespace, identity, dataConverterInstance, interceptorInstance, contextPropagatorInstance);
    }

    private static WorkflowClientOptions build(
            String namespace,
            Optional<String> identity,
            Instance<DataConverter> dataConverterInstance,
            Instance<WorkflowClientInterceptor> interceptorInstance,
            Instance<ContextPropagator> contextPropagatorInstance) {

        WorkflowClientOptions.Builder builder = WorkflowClientOptions.newBuilder()
                .setNamespace(namespace);

        identity.ifPresent(builder::setIdentity);

        DataConverter dataConverter = dataConverterInstance.isResolvable()
                ? dataConverterInstance.get()
                : null;

        if (dataConverter != null) {
            builder.setDataConverter(dataConverter);
        }

        List<WorkflowClientInterceptor> interceptors = interceptorInstance.stream()
                .collect(Collectors.toCollection(ArrayList::new));

        if (!interceptors.isEmpty()) {
            builder.setInterceptors(interceptors.toArray(new WorkflowClientInterceptor[0]));
        }

        List<ContextPropagator> propagators = contextPropagatorInstance.stream()
                .collect(Collectors.toCollection(ArrayList::new));
        if (!propagators.isEmpty()) {
            builder.setContextPropagators(propagators);
        }

        return builder.validateAndBuildWithDefaults();
    }
}
