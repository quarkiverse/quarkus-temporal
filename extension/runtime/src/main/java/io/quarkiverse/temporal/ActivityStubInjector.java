package io.quarkiverse.temporal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.configuration.DurationConverter;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.common.converter.EncodedValues;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import io.temporal.workflow.WorkflowInterface;

/**
 * Creates workflow implementation instances and injects activity stubs into fields annotated with
 * {@link TemporalActivityStub}.
 * <p>
 * Instances are created from the workflow thread, so calling {@link Workflow#newActivityStub} is legal and
 * yields the same deterministic behavior as creating the stub in a field initializer. When the implementation
 * declares a {@link WorkflowInit} constructor, the workflow input is decoded and passed to it, exactly as the
 * SDK does for workflows registered by type.
 */
public final class ActivityStubInjector {

    private final Class<?> implementation;
    private final Class<?> workflowInterface;
    private final Constructor<?> constructor;
    private final List<Field> fields;
    private final List<TemporalActivityStub> fieldStubs;

    private ActivityStubInjector(Class<?> implementation, Class<?> workflowInterface, Constructor<?> constructor,
            List<Field> fields, List<TemporalActivityStub> fieldStubs) {
        this.implementation = implementation;
        this.workflowInterface = workflowInterface;
        this.constructor = constructor;
        this.fields = fields;
        this.fieldStubs = fieldStubs;
    }

    /**
     * @return {@code true} when the class declares at least one {@link TemporalActivityStub} injection point
     */
    public static boolean hasInjectionPoints(Class<?> implementation) {
        for (Class<?> current = implementation; current != null && current != Object.class; current = current
                .getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(TemporalActivityStub.class)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Inspects the workflow implementation and builds the injection plan. Fails fast when the class cannot be
     * instantiated or when an injection point does not target an activity interface.
     */
    public static ActivityStubInjector forClass(Class<?> implementation) {
        Class<?> workflowInterface = findWorkflowInterface(implementation);
        Constructor<?> constructor = findConstructor(implementation);
        List<Field> fields = new ArrayList<>();
        List<TemporalActivityStub> fieldStubs = new ArrayList<>();
        for (Class<?> current = implementation; current != null && current != Object.class; current = current
                .getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                TemporalActivityStub stub = field.getAnnotation(TemporalActivityStub.class);
                if (stub == null) {
                    continue;
                }
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new IllegalStateException(
                            "Static field " + field + " cannot be annotated with @TemporalActivityStub");
                }
                if (!field.getType().isInterface() || !field.getType().isAnnotationPresent(ActivityInterface.class)) {
                    throw new IllegalStateException("@TemporalActivityStub injection point \'" + field.getName()
                            + "\' of workflow " + implementation.getName() + " must be an @ActivityInterface, but is "
                            + field.getType().getName());
                }
                field.setAccessible(true);
                fields.add(field);
                fieldStubs.add(stub);
            }
        }
        constructor.setAccessible(true);
        return new ActivityStubInjector(implementation, workflowInterface, constructor, fields, fieldStubs);
    }

    /**
     * @return the workflow interface implemented by the workflow implementation
     */
    public Class<?> workflowInterface() {
        return workflowInterface;
    }

    /**
     * Creates a new workflow instance with all activity stubs injected. Must be invoked from a workflow thread.
     *
     * @param input the encoded workflow input, passed to the {@link WorkflowInit} constructor when there is one
     */
    public Object newInstance(EncodedValues input) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Type[] genericParameterTypes = constructor.getGenericParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = input.get(i, parameterTypes[i], genericParameterTypes[i]);
        }
        Object instance;
        try {
            instance = constructor.newInstance(arguments);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to instantiate workflow " + implementation.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Unable to instantiate workflow " + implementation.getName(), cause);
        }
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            try {
                field.set(instance, newStub(field.getType(), fieldStubs.get(i)));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to inject activity stub into " + field, e);
            }
        }
        return instance;
    }

    static Object newStub(Class<?> activityInterface, TemporalActivityStub stub) {
        if (stub.local()) {
            return Workflow.newLocalActivityStub(activityInterface, localActivityOptions(stub));
        }
        return Workflow.newActivityStub(activityInterface, activityOptions(stub));
    }

    public static ActivityOptions activityOptions(TemporalActivityStub stub) {
        ActivityOptions.Builder builder = ActivityOptions.newBuilder();
        duration(stub.startToCloseTimeout()).ifPresent(builder::setStartToCloseTimeout);
        duration(stub.scheduleToCloseTimeout()).ifPresent(builder::setScheduleToCloseTimeout);
        duration(stub.scheduleToStartTimeout()).ifPresent(builder::setScheduleToStartTimeout);
        duration(stub.heartbeatTimeout()).ifPresent(builder::setHeartbeatTimeout);
        if (!stub.taskQueue().isEmpty()) {
            builder.setTaskQueue(stub.taskQueue());
        }
        builder.setCancellationType(stub.cancellationType());
        retryOptions(stub).ifPresent(builder::setRetryOptions);
        return builder.build();
    }

    public static LocalActivityOptions localActivityOptions(TemporalActivityStub stub) {
        LocalActivityOptions.Builder builder = LocalActivityOptions.newBuilder();
        duration(stub.startToCloseTimeout()).ifPresent(builder::setStartToCloseTimeout);
        duration(stub.scheduleToCloseTimeout()).ifPresent(builder::setScheduleToCloseTimeout);
        duration(stub.scheduleToStartTimeout()).ifPresent(builder::setScheduleToStartTimeout);
        retryOptions(stub).ifPresent(builder::setRetryOptions);
        return builder.build();
    }

    static Optional<RetryOptions> retryOptions(TemporalActivityStub stub) {
        boolean configured = stub.retryMaximumAttempts() > 0
                || stub.retryBackoffCoefficient() > 0
                || !stub.retryInitialInterval().isEmpty()
                || !stub.retryMaximumInterval().isEmpty()
                || stub.retryDoNotRetry().length > 0;
        if (!configured) {
            return Optional.empty();
        }
        RetryOptions.Builder builder = RetryOptions.newBuilder();
        if (stub.retryMaximumAttempts() > 0) {
            builder.setMaximumAttempts(stub.retryMaximumAttempts());
        }
        if (stub.retryBackoffCoefficient() > 0) {
            builder.setBackoffCoefficient(stub.retryBackoffCoefficient());
        }
        duration(stub.retryInitialInterval()).ifPresent(builder::setInitialInterval);
        duration(stub.retryMaximumInterval()).ifPresent(builder::setMaximumInterval);
        if (stub.retryDoNotRetry().length > 0) {
            String[] doNotRetry = new String[stub.retryDoNotRetry().length];
            for (int i = 0; i < doNotRetry.length; i++) {
                doNotRetry[i] = stub.retryDoNotRetry()[i].getName();
            }
            builder.setDoNotRetry(doNotRetry);
        }
        return Optional.of(builder.build());
    }

    static Optional<Duration> duration(String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DurationConverter.parseDuration(value));
    }

    private static Class<?> findWorkflowInterface(Class<?> implementation) {
        List<Class<?>> found = new ArrayList<>();
        collectWorkflowInterfaces(implementation, found);
        if (found.isEmpty()) {
            throw new IllegalStateException(
                    "Workflow " + implementation.getName() + " does not implement a @WorkflowInterface");
        }
        if (found.size() > 1) {
            throw new IllegalStateException("Workflow " + implementation.getName()
                    + " implements more than one @WorkflowInterface, which is not supported together with @TemporalActivityStub injection: "
                    + found);
        }
        return found.get(0);
    }

    private static void collectWorkflowInterfaces(Class<?> type, List<Class<?>> found) {
        if (type == null) {
            return;
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (iface.isAnnotationPresent(WorkflowInterface.class) && !found.contains(iface)) {
                found.add(iface);
            }
            collectWorkflowInterfaces(iface, found);
        }
        collectWorkflowInterfaces(type.getSuperclass(), found);
    }

    /**
     * Mirrors the SDK rules: a public {@link WorkflowInit} constructor wins, otherwise a no-args constructor
     * is required.
     */
    private static Constructor<?> findConstructor(Class<?> implementation) {
        Constructor<?> init = null;
        Constructor<?> noArgs = null;
        for (Constructor<?> candidate : implementation.getDeclaredConstructors()) {
            if (candidate.isAnnotationPresent(WorkflowInit.class)) {
                if (init != null) {
                    throw new IllegalStateException(
                            "Workflow " + implementation.getName() + " has more than one @WorkflowInit constructor");
                }
                if (!Modifier.isPublic(candidate.getModifiers())) {
                    throw new IllegalStateException(
                            "@WorkflowInit constructor of workflow " + implementation.getName() + " must be public");
                }
                init = candidate;
            } else if (candidate.getParameterCount() == 0) {
                noArgs = candidate;
            }
        }
        if (init != null) {
            return init;
        }
        if (noArgs != null) {
            return noArgs;
        }
        throw new IllegalStateException("Workflow " + implementation.getName()
                + " needs a no-args constructor or a @WorkflowInit constructor");
    }
}
