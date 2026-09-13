package io.quarkiverse.temporal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.configuration.DurationConverter;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;

/**
 * Creates workflow implementation instances and injects activity stubs into members annotated with
 * {@link TemporalActivityStub}.
 * <p>
 * Instances are created from the workflow thread, so calling {@link Workflow#newActivityStub} is legal and
 * yields the same deterministic behavior as creating the stub in a field initializer.
 */
public final class ActivityStubInjector {

    private final Class<?> implementation;
    private final Class<?> workflowInterface;
    private final Constructor<?> constructor;
    private final List<TemporalActivityStub> constructorStubs;
    private final List<Field> fields;
    private final List<TemporalActivityStub> fieldStubs;

    private ActivityStubInjector(Class<?> implementation, Class<?> workflowInterface, Constructor<?> constructor,
            List<TemporalActivityStub> constructorStubs, List<Field> fields, List<TemporalActivityStub> fieldStubs) {
        this.implementation = implementation;
        this.workflowInterface = workflowInterface;
        this.constructor = constructor;
        this.constructorStubs = constructorStubs;
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
        for (Constructor<?> candidate : implementation.getDeclaredConstructors()) {
            for (Parameter parameter : candidate.getParameters()) {
                if (parameter.isAnnotationPresent(TemporalActivityStub.class)) {
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
        List<TemporalActivityStub> constructorStubs = new ArrayList<>();
        for (Parameter parameter : constructor.getParameters()) {
            TemporalActivityStub stub = parameter.getAnnotation(TemporalActivityStub.class);
            if (stub == null) {
                throw new IllegalStateException("Constructor " + constructor + " of workflow " + implementation.getName()
                        + " has a parameter that is not annotated with @TemporalActivityStub");
            }
            validateActivityInterface(parameter.getType(), implementation, parameter.getName());
            constructorStubs.add(stub);
        }

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
                validateActivityInterface(field.getType(), implementation, field.getName());
                field.setAccessible(true);
                fields.add(field);
                fieldStubs.add(stub);
            }
        }
        constructor.setAccessible(true);
        return new ActivityStubInjector(implementation, workflowInterface, constructor, constructorStubs, fields,
                fieldStubs);
    }

    /**
     * @return the workflow interface implemented by the workflow implementation
     */
    public Class<?> workflowInterface() {
        return workflowInterface;
    }

    /**
     * Creates a new workflow instance with all activity stubs injected. Must be invoked from a workflow thread.
     */
    public Object newInstance() {
        Object[] arguments = new Object[constructorStubs.size()];
        Parameter[] parameters = constructor.getParameters();
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = newStub(parameters[i].getType(), constructorStubs.get(i));
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
                || !stub.retryMaximumInterval().isEmpty();
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

    private static Constructor<?> findConstructor(Class<?> implementation) {
        Constructor<?>[] constructors = implementation.getDeclaredConstructors();
        Constructor<?> annotated = null;
        Constructor<?> noArgs = null;
        for (Constructor<?> candidate : constructors) {
            if (candidate.getParameterCount() == 0) {
                noArgs = candidate;
                continue;
            }
            if (hasAnnotatedParameter(candidate)) {
                if (annotated != null) {
                    throw new IllegalStateException("Workflow " + implementation.getName()
                            + " has more than one constructor with @TemporalActivityStub parameters");
                }
                annotated = candidate;
            }
        }
        if (annotated != null) {
            return annotated;
        }
        if (noArgs != null) {
            return noArgs;
        }
        throw new IllegalStateException("Workflow " + implementation.getName()
                + " needs a no-args constructor or a constructor whose parameters are all annotated with @TemporalActivityStub");
    }

    private static boolean hasAnnotatedParameter(Constructor<?> constructor) {
        for (Annotation[] annotations : constructor.getParameterAnnotations()) {
            for (Annotation annotation : annotations) {
                if (annotation instanceof TemporalActivityStub) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void validateActivityInterface(Class<?> type, Class<?> implementation, String member) {
        if (!type.isInterface() || !type.isAnnotationPresent(ActivityInterface.class)) {
            throw new IllegalStateException("@TemporalActivityStub injection point '" + member + "' of workflow "
                    + implementation.getName() + " must be an @ActivityInterface, but is " + type.getName());
        }
    }
}
