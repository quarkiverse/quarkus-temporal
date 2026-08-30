# Per-Test-Fresh Mock TestWorkflowEnvironment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `quarkus-temporal-test`'s injected `TestWorkflowEnvironment`/`WorkflowClient`/`WorkerFactory` fresh for every `@Test` method instead of one shared, leaking, `Singleton`-scoped instance for the whole test JVM run — fixing the bug demonstrated in [PR #233](https://github.com/quarkiverse/quarkus-temporal/pull/233) — without requiring any change to user test code.

**Architecture:** `TestWorkflowEnvironment` becomes `ApplicationScoped` and `WorkflowClient` stays `ApplicationScoped` (already was); both get swapped per test via `QuarkusMock.installMockForType(...)` in a new `QuarkusTestBeforeEachCallback`. `WorkerFactory` is a `final` SDK class with only a `private` constructor and can't be CDI-proxied, so it becomes `@Dependent`-scoped instead, reading "whatever is currently prepared" from a plain static holder; a "prepare-ahead" `QuarkusTestAfterEachCallback` builds test N+1's environment during test N's teardown so it's already in the holder by the time test N+1 is constructed (CDI resolves `@Dependent` fields at construction time, before any callback can run). Worker/workflow-type registration (normally a one-time boot step) is captured as replayable closures and re-run against every fresh environment.

**Tech Stack:** Quarkus 3.33.1 (Java, Maven multi-module), Temporal Java SDK (`temporal-sdk`, `temporal-testing`), JUnit 5, `quarkus-junit`'s test-lifecycle callback SPI (`io.quarkus.test.junit.callback.*`), Mockito (test-only, for the fixture regression coverage).

**Spec:** `docs/superpowers/specs/2026-08-30-per-test-mock-environment-design.md`

## Global Constraints

- No changes to production (non-mock) code paths in `extension`. All new/changed behavior is gated behind `quarkus.temporal.enable-mock=true` (build-time) or is otherwise inert unless the `quarkus-temporal-test` extension is present.
- No bytecode transforms (`BytecodeTransformerBuildItem`) anywhere in this design — that approach was tried and rejected (see spec, "Constraints discovered during investigation" #5, and "Approaches considered").
- Every new dependency version comes from the existing `quarkus-bom` import (no explicit `<version>` needed for `quarkus-junit` or `mockito-core`).
- Regression/behavioral tests for the actual per-test-freshness fix MUST live in `integration-tests` (real `@QuarkusTest`, goes through the full `QuarkusTestExtension` lifecycle). `test-extension/deployment`'s `QuarkusUnitTest`-based tests do **not** invoke the `QuarkusTestBeforeEachCallback`/`QuarkusTestAfterEachCallback` SPI this fix relies on (verified by reading `QuarkusUnitTest`'s source) — a test written there would prove nothing about this fix.
- Follow existing code style: 4-space indent, no javadoc beyond what's shown below, package-private where the existing code is package-private.

---

### Task 1: Capture worker registration for replay (`extension/runtime`)

**Files:**
- Create: `extension/runtime/src/main/java/io/quarkiverse/temporal/WorkerRegistrationRegistry.java`
- Modify: `extension/runtime/src/main/java/io/quarkiverse/temporal/WorkerFactoryRecorder.java:196-214`

**Interfaces:**
- Produces: `WorkerRegistrationRegistry.record(Runnable)` and `WorkerRegistrationRegistry.replayAll()` — a static registry other modules (Task 6's callback) call into to re-run worker/workflow-type registration against a freshly created `WorkerFactory`.
- Consumes: nothing new; `WorkerFactoryRecorder`'s existing fields (`runtimeConfig`, `buildtimeConfig`) and existing private methods (`createWorkerOptions`, `createQueueName`) are unchanged.

This task has no externally-observable behavior change yet (worker creation logic is identical, just also recorded for later replay) — it's verified by confirming the existing test suite still passes.

- [ ] **Step 1: Create the registry**

```java
package io.quarkiverse.temporal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Captures worker/workflow-type registrations performed at boot so they can be replayed
 * against a freshly created {@link io.temporal.worker.WorkerFactory} in test/mock mode.
 *
 * Populated unconditionally by {@link WorkerFactoryRecorder#createWorker}; only ever read
 * by the {@code quarkus-temporal-test} extension.
 */
public final class WorkerRegistrationRegistry {

    private static final List<Runnable> REGISTRATIONS = new CopyOnWriteArrayList<>();

    private WorkerRegistrationRegistry() {
    }

    public static void record(Runnable registration) {
        REGISTRATIONS.add(registration);
    }

    public static void replayAll() {
        for (Runnable registration : REGISTRATIONS) {
            registration.run();
        }
    }
}
```

- [ ] **Step 2: Extract `createWorker`'s body into `doCreateWorker`, and record a replay closure**

In `WorkerFactoryRecorder.java`, replace the existing `createWorker` method (lines 196-214):

```java
    public void createWorker(String name, List<Class<?>> workflows,
            List<Class<?>> activities) {
        // Workers are created during runtime init and registered with workflow/activity implementations.
        // Starting polling threads is intentionally deferred to startWorkerFactory(...).
        WorkerFactory workerFactory = CDI.current().select(WorkerFactory.class).get();
        WorkerRuntimeConfig workerRuntimeConfig = runtimeConfig.getValue().worker().get(name);
        WorkerBuildtimeConfig workerBuildtimeConfig = buildtimeConfig.worker().get(name);

        Worker worker = workerFactory.newWorker(createQueueName(name, workerRuntimeConfig),
                createWorkerOptions(workerRuntimeConfig, workerBuildtimeConfig));
        for (var workflow : workflows) {
            worker.registerWorkflowImplementationTypes(workflow);
        }
        if (buildtimeConfig.startWorkers()) {
            for (var activity : activities) {
                worker.registerActivitiesImplementations(CDI.current().select(activity).get());
            }
        }
    }
```

with:

```java
    public void createWorker(String name, List<Class<?>> workflows,
            List<Class<?>> activities) {
        WorkerRegistrationRegistry.record(() -> doCreateWorker(name, workflows, activities));
        doCreateWorker(name, workflows, activities);
    }

    private void doCreateWorker(String name, List<Class<?>> workflows,
            List<Class<?>> activities) {
        // Workers are created during runtime init (or replayed per test in mock mode) and
        // registered with workflow/activity implementations.
        // Starting polling threads is intentionally deferred to startWorkerFactory(...).
        WorkerFactory workerFactory = CDI.current().select(WorkerFactory.class).get();
        WorkerRuntimeConfig workerRuntimeConfig = runtimeConfig.getValue().worker().get(name);
        WorkerBuildtimeConfig workerBuildtimeConfig = buildtimeConfig.worker().get(name);

        Worker worker = workerFactory.newWorker(createQueueName(name, workerRuntimeConfig),
                createWorkerOptions(workerRuntimeConfig, workerBuildtimeConfig));
        for (var workflow : workflows) {
            worker.registerWorkflowImplementationTypes(workflow);
        }
        if (buildtimeConfig.startWorkers()) {
            for (var activity : activities) {
                worker.registerActivitiesImplementations(CDI.current().select(activity).get());
            }
        }
    }
```

- [ ] **Step 3: Build and run the existing extension test suite to confirm no regression**

Run: `mvn -q -pl extension/runtime,extension/deployment -am test`
Expected: BUILD SUCCESS, all existing tests pass (no behavior change yet).

- [ ] **Step 4: Commit**

```bash
git add extension/runtime/src/main/java/io/quarkiverse/temporal/WorkerRegistrationRegistry.java extension/runtime/src/main/java/io/quarkiverse/temporal/WorkerFactoryRecorder.java
git commit -m "feat: capture worker registration for replay in mock/test mode"
```

---

### Task 2: Add a CDI-current variant of `WorkflowClientOptionsSupport` (`extension/runtime`)

**Files:**
- Modify: `extension/runtime/src/main/java/io/quarkiverse/temporal/WorkflowClientOptionsSupport.java`

**Interfaces:**
- Produces: `WorkflowClientOptionsSupport.buildFromCurrentCdi(String namespace, Optional<String> identity)` — same result as `buildFromContext`, but resolvable outside of synthetic-bean creation (needed by Task 5's JUnit callback, which isn't inside a `SyntheticCreationalContext`).
- Consumes: nothing new.

- [ ] **Step 1: Refactor to share logic between `buildFromContext` and a new `buildFromCurrentCdi`**

Replace the full contents of `WorkflowClientOptionsSupport.java` with:

```java
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
```

- [ ] **Step 2: Build and run the existing extension + test-extension suite to confirm no regression**

Run: `mvn -q -pl extension/runtime,extension/deployment,test-extension/runtime,test-extension/deployment -am test`
Expected: BUILD SUCCESS, all existing tests pass.

- [ ] **Step 3: Commit**

```bash
git add extension/runtime/src/main/java/io/quarkiverse/temporal/WorkflowClientOptionsSupport.java
git commit -m "refactor: extract CDI.current()-based variant of WorkflowClientOptionsSupport"
```

---

### Task 3: Add the prepare-ahead holder (`test-extension/runtime`)

**Files:**
- Create: `test-extension/runtime/src/main/java/io/quarkiverse/temporal/test/MockTestEnvironmentHolder.java`
- Modify: `test-extension/runtime/src/main/java/io/quarkiverse/temporal/test/TestWorkflowRecorder.java`

**Interfaces:**
- Produces: `MockTestEnvironmentHolder.current()` (returns `TestWorkflowEnvironment`, may be `null` before boot seeding) and `MockTestEnvironmentHolder.set(TestWorkflowEnvironment)` — consumed by Task 4's `WorkerFactory` synthetic bean producer and Task 5's callback.
- Consumes: nothing new.

- [ ] **Step 1: Create the holder**

```java
package io.quarkiverse.temporal.test;

import io.temporal.testing.TestWorkflowEnvironment;

/**
 * Holds the {@link TestWorkflowEnvironment} currently prepared for the in-progress (or
 * about-to-start) test. Deliberately a plain static holder, not a CDI bean, so the
 * {@code @Dependent}-scoped {@code WorkerFactory} synthetic bean can read it at CDI
 * injection time - which happens at test-instance construction, before any JUnit
 * lifecycle callback has a chance to run.
 */
public final class MockTestEnvironmentHolder {

    private static volatile TestWorkflowEnvironment current;

    private MockTestEnvironmentHolder() {
    }

    public static TestWorkflowEnvironment current() {
        return current;
    }

    public static void set(TestWorkflowEnvironment environment) {
        current = environment;
    }
}
```

- [ ] **Step 2: Wire `TestWorkflowRecorder` to seed and read the holder**

Replace the full contents of `TestWorkflowRecorder.java` with:

```java
package io.quarkiverse.temporal.test;

import java.util.Optional;
import java.util.function.Function;

import io.quarkiverse.temporal.WorkflowClientOptionsSupport;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.WorkerFactory;

@Recorder
public class TestWorkflowRecorder {
    public Function<SyntheticCreationalContext<TestWorkflowEnvironment>, TestWorkflowEnvironment> createTestWorkflowEnvironment() {
        return context -> {
            TestEnvironmentOptions options = TestEnvironmentOptions.newBuilder()
                    .setWorkflowClientOptions(createTestWorkflowClientOptions(context))
                    .build();

            TestWorkflowEnvironment environment = TestWorkflowEnvironment.newInstance(options);
            // Seed the holder immediately: the @Dependent WorkerFactory bean (and the
            // first test's MockTestWorkflowResetCallback) both read from it.
            MockTestEnvironmentHolder.set(environment);
            return environment;
        };
    }

    /**
     * Builds the {@link WorkflowClientOptions} used by the mock TestWorkflowEnvironment, while honoring the CDI wiring.
     */
    public WorkflowClientOptions createTestWorkflowClientOptions(SyntheticCreationalContext<?> context) {
        return WorkflowClientOptionsSupport.buildFromContext(
                context,
                "default",
                Optional.empty());
    }

    public Function<SyntheticCreationalContext<WorkflowClient>, WorkflowClient> createTestWorkflowClient() {
        return context -> {
            TestWorkflowEnvironment testWorkflowEnvironment = context.getInjectedReference(TestWorkflowEnvironment.class);
            return testWorkflowEnvironment.getWorkflowClient();
        };
    }

    public Function<SyntheticCreationalContext<WorkerFactory>, WorkerFactory> createTestWorkerFactory() {
        return context -> {
            // TestWorkflowEnvironment is ApplicationScoped (proxied): merely obtaining the
            // injected reference does not trigger its creation, only invoking a method on it
            // does. That first creation is what seeds MockTestEnvironmentHolder, which is
            // then read below (not the value returned here) so later tests - which swap the
            // holder directly - are picked up too.
            TestWorkflowEnvironment testWorkflowEnvironment = context.getInjectedReference(TestWorkflowEnvironment.class);
            testWorkflowEnvironment.getWorkerFactory();
            return MockTestEnvironmentHolder.current().getWorkerFactory();
        };
    }
}
```

- [ ] **Step 3: Compile to confirm no errors (behavior not yet exercised until Task 4 changes the bean scopes)**

Run: `mvn -q -pl test-extension/runtime -am compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add test-extension/runtime/src/main/java/io/quarkiverse/temporal/test/MockTestEnvironmentHolder.java test-extension/runtime/src/main/java/io/quarkiverse/temporal/test/TestWorkflowRecorder.java
git commit -m "feat: add prepare-ahead holder for the mock TestWorkflowEnvironment"
```

---

### Task 4: Change synthetic bean scopes (`test-extension/deployment`)

**Files:**
- Modify: `test-extension/deployment/src/main/java/io/quarkiverse/temporal/test/deployment/TemporalTestProcessor.java`

**Interfaces:**
- Consumes: `TestWorkflowRecorder.createTestWorkflowEnvironment()`, `.createTestWorkflowClient()`, `.createTestWorkerFactory()` (Task 3, unchanged signatures).
- Produces: the `TestWorkflowEnvironment`, `WorkflowClient`, `WorkerFactory` synthetic beans that Task 5's callback and Task 6's IT tests inject.

- [ ] **Step 1: Change `TestWorkflowEnvironment`'s scope to `ApplicationScoped` and `WorkerFactory`'s scope to `Dependent`**

Replace the full contents of `TemporalTestProcessor.java` with:

```java
package io.quarkiverse.temporal.test.deployment;

import static io.quarkiverse.temporal.Constants.TEMPORAL_TESTING_CAPABILITY;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.ParameterizedType;

import io.quarkiverse.temporal.deployment.TemporalProcessor;
import io.quarkiverse.temporal.test.TestWorkflowRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CapabilityBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.temporal.client.WorkflowClient;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.interceptors.WorkflowClientInterceptor;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.WorkerFactory;

public class TemporalTestProcessor {

    private static final String FEATURE = "temporal-test";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void capabilities(BuildProducer<CapabilityBuildItem> capabilityProducer) {
        capabilityProducer.produce(new CapabilityBuildItem(TEMPORAL_TESTING_CAPABILITY, "temporal"));
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    SyntheticBeanBuildItem recordTestEnvironment(TestWorkflowRecorder recorder) {
        return SyntheticBeanBuildItem
                .configure(TestWorkflowEnvironment.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .addInjectionPoint(
                        ParameterizedType.create(Instance.class, ClassType.create(WorkflowClientInterceptor.class)),
                        AnnotationInstance.builder(Any.class).build())
                .addInjectionPoint(
                        ParameterizedType.create(Instance.class, ClassType.create(ContextPropagator.class)),
                        AnnotationInstance.builder(Any.class).build())
                .addInjectionPoint(
                        ParameterizedType.create(Instance.class, ClassType.create(DataConverter.class)),
                        AnnotationInstance.builder(Any.class).build())
                .createWith(recorder.createTestWorkflowEnvironment())
                .setRuntimeInit()
                .done();
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    SyntheticBeanBuildItem recordWorkflowClient(TestWorkflowRecorder recorder) {

        return SyntheticBeanBuildItem
                .configure(WorkflowClient.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .defaultBean()
                .addInjectionPoint(ClassType.create(TestWorkflowEnvironment.class))
                .createWith(recorder.createTestWorkflowClient())
                .setRuntimeInit()
                .done();
    }

    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    SyntheticBeanBuildItem produceWorkerFactorySyntheticBean(TestWorkflowRecorder recorder) {
        // @Dependent (not a normal scope): io.temporal.worker.WorkerFactory is a final class
        // with only a private constructor and cannot be CDI-proxied. A fresh instance is
        // instead resolved from MockTestEnvironmentHolder at every injection point - see
        // MockTestEnvironmentHolder / MockTestWorkflowResetCallback for how freshness is
        // still achieved per test.
        // The TestWorkflowEnvironment injection point below is not read directly by
        // createTestWorkerFactory() any more, but it must stay: declaring (and resolving) it
        // is what forces ArC to create the TestWorkflowEnvironment bean - and so seed the
        // holder - before this bean is ever created.
        return SyntheticBeanBuildItem
                .configure(WorkerFactory.class)
                .scope(Dependent.class)
                .unremovable()
                .defaultBean()
                .addInjectionPoint(ClassType.create(TestWorkflowEnvironment.class))
                .createWith(recorder.createTestWorkerFactory())
                .setRuntimeInit()
                .done();
    }
}
```

Note what changed from the original: `Singleton` import removed (no longer used), `Dependent` import added; `recordTestEnvironment`'s scope is now `ApplicationScoped`; `produceWorkerFactorySyntheticBean`'s scope is now `Dependent` (its `WorkflowClient` injection point, unused for the same reason, is removed - but its `TestWorkflowEnvironment` injection point must stay, see below).

**Discovered while implementing this task:** removing the `TestWorkflowEnvironment` injection point entirely broke boot - `WorkerFactoryRecorder.startWorkerFactory` resolves `WorkerFactory` before anything else forces `TestWorkflowEnvironment` to be created (e.g. in a test with no declared workers, nothing else ever touches it), so `MockTestEnvironmentHolder.current()` was `null`. Declaring the injection point isn't enough by itself either - `TestWorkflowEnvironment` is a normal-scoped (proxied) bean now, so merely obtaining the injected reference doesn't trigger creation, only invoking a method on it does (this is why `createTestWorkflowClient()` already worked unchanged - it calls `.getWorkflowClient()`). The fix, reflected in Task 3's final `createTestWorkerFactory()` above: keep the injection point, resolve it, and call `.getWorkerFactory()` on it (discarding the result) purely to force creation/holder-seeding, then read the actual return value from the holder.

- [ ] **Step 2: Build and run the existing test-extension suite to confirm no regression**

Run: `mvn -q -pl test-extension/runtime,test-extension/deployment -am test`
Expected: BUILD SUCCESS, all existing tests (`MockEnabledTest`, etc.) pass. (`StartWorkersEnabledTest`/`StartWorkersDisabledTest` are pre-existing `@Disabled` tests, unaffected either way — see Task 7.)

- [ ] **Step 3: Commit**

```bash
git add test-extension/deployment/src/main/java/io/quarkiverse/temporal/test/deployment/TemporalTestProcessor.java
git commit -m "fix: scope TestWorkflowEnvironment as ApplicationScoped, WorkerFactory as Dependent"
```

---

### Task 5: The per-test reset callback (`test-extension/runtime`)

**Files:**
- Modify: `test-extension/runtime/pom.xml`
- Create: `test-extension/runtime/src/main/java/io/quarkiverse/temporal/test/MockTestWorkflowResetCallback.java`
- Create: `test-extension/runtime/src/main/resources/META-INF/services/io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback`
- Create: `test-extension/runtime/src/main/resources/META-INF/services/io.quarkus.test.junit.callback.QuarkusTestAfterEachCallback`

**Interfaces:**
- Consumes: `MockTestEnvironmentHolder.current()`/`.set(...)` (Task 3), `WorkerRegistrationRegistry.replayAll()` (Task 1), `WorkflowClientOptionsSupport.buildFromCurrentCdi(...)` (Task 2).
- Produces: nothing new consumed by later tasks — this is the last piece of the production fix. Tasks 6/7 exercise it end-to-end.

- [ ] **Step 1: Add the `quarkus-junit` dependency**

In `test-extension/runtime/pom.xml`, add inside `<dependencies>`:

```xml
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit</artifactId>
            <scope>provided</scope>
        </dependency>
```

**Discovered while implementing this task:** two corrections from what's written above.

First, `quarkus-junit5` (the artifact one might expect from older Quarkus docs) is not what provides `QuarkusMock`/the callback SPI in this Quarkus version (3.33.1) - `quarkus-junit` is, and it's also the artifact `integration-tests` already uses for its own `@QuarkusTest` classes, so this keeps the whole repo on one convention.

Second, `<scope>provided</scope>` is required, not optional: `quarkus-junit` (like `quarkus-junit5`) legitimately depends on `-deployment` artifacts (needed for `@QuarkusTest`'s own re-augmentation support), and the `quarkus-extension-maven-plugin`'s dependency verification step correctly rejects any `-deployment` artifact appearing on an extension runtime module's default (compile-scope) classpath - real consumers must never see build-time artifacts on their production classpath. `provided` scope makes the classes available for compiling this module without tripping that check. This doesn't leave consumers missing anything at runtime: anyone using `@QuarkusTest` (which is what's needed for this callback to ever run at all) already has `quarkus-junit` on their own test classpath directly, regardless of what `quarkus-temporal-test` declares.

- [ ] **Step 2: Write the callback**

```java
package io.quarkiverse.temporal.test;

import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.temporal.WorkerRegistrationRegistry;
import io.quarkiverse.temporal.WorkflowClientOptionsSupport;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.callback.QuarkusTestAfterEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;

/**
 * Gives every {@code @Test} method its own {@link TestWorkflowEnvironment} (and derived
 * {@link WorkflowClient}/{@link io.temporal.worker.WorkerFactory}) instead of the single,
 * shared, leaking instance the extension previously handed out for the whole test JVM run.
 *
 * <p>{@code TestWorkflowEnvironment} and {@code WorkflowClient} are swapped per test via
 * {@link QuarkusMock}. {@code WorkerFactory} cannot use the same mechanism (it's a final SDK
 * class with only a private constructor, so ArC can't generate a client proxy for it) - it is
 * instead {@code @Dependent}-scoped and reads the "currently prepared" environment from
 * {@link MockTestEnvironmentHolder}, which this callback prepares one test ahead: CDI resolves
 * {@code @Dependent} fields at test-construction time, before {@link #beforeEach} can run, so
 * the fresh instance has to already be in the holder by then.
 */
public class MockTestWorkflowResetCallback implements QuarkusTestBeforeEachCallback, QuarkusTestAfterEachCallback {

    private static final Logger log = Logger.getLogger(MockTestWorkflowResetCallback.class);

    private TestWorkflowEnvironment inUseForCurrentTest;

    @Override
    public void beforeEach(QuarkusTestMethodContext context) {
        if (!isMockEnabled()) {
            return;
        }
        // Already prepared - by app boot (the very first test) or by the previous test's
        // afterEach (every test after that).
        TestWorkflowEnvironment current = MockTestEnvironmentHolder.current();
        if (current == null) {
            return;
        }
        QuarkusMock.installMockForType(current, TestWorkflowEnvironment.class);
        QuarkusMock.installMockForType(current.getWorkflowClient(), WorkflowClient.class);
        this.inUseForCurrentTest = current;
    }

    @Override
    public void afterEach(QuarkusTestMethodContext context) {
        if (!isMockEnabled()) {
            return;
        }
        TestWorkflowEnvironment justUsed = this.inUseForCurrentTest;
        this.inUseForCurrentTest = null;
        if (justUsed != null) {
            shutdownQuietly(justUsed);
        }
        prepareNext();
    }

    private void prepareNext() {
        TestEnvironmentOptions options = TestEnvironmentOptions.newBuilder()
                .setWorkflowClientOptions(WorkflowClientOptionsSupport.buildFromCurrentCdi("default", Optional.empty()))
                .build();
        TestWorkflowEnvironment next = TestWorkflowEnvironment.newInstance(options);

        MockTestEnvironmentHolder.set(next);
        WorkerRegistrationRegistry.replayAll();

        if (isStartWorkersEnabled()) {
            next.getWorkerFactory().start();
        }
    }

    private void shutdownQuietly(TestWorkflowEnvironment environment) {
        try {
            environment.getWorkerFactory().shutdown();
        } catch (RuntimeException e) {
            log.debugf(e, "Ignoring failure shutting down worker factory - likely already shut down by the test");
        }
        try {
            environment.close();
        } catch (RuntimeException e) {
            log.debugf(e, "Ignoring failure closing test workflow environment - likely already closed by the test");
        }
    }

    private static boolean isMockEnabled() {
        return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.temporal.enable-mock", Boolean.class)
                .orElse(false);
    }

    private static boolean isStartWorkersEnabled() {
        return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.temporal.start-workers", Boolean.class)
                .orElse(false);
    }
}
```

- [ ] **Step 3: Register the callback for auto-discovery**

Create `test-extension/runtime/src/main/resources/META-INF/services/io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback` containing exactly:

```
io.quarkiverse.temporal.test.MockTestWorkflowResetCallback
```

Create `test-extension/runtime/src/main/resources/META-INF/services/io.quarkus.test.junit.callback.QuarkusTestAfterEachCallback` containing exactly:

```
io.quarkiverse.temporal.test.MockTestWorkflowResetCallback
```

- [ ] **Step 4: Build and run the existing test-extension suite to confirm no regression**

Run: `mvn -q -pl test-extension/runtime,test-extension/deployment -am test`
Expected: BUILD SUCCESS. (Still no new *behavioral* proof yet - `test-extension/deployment`'s tests use `QuarkusUnitTest`, which doesn't invoke this callback at all. Task 6 is where this gets actually exercised.)

- [ ] **Step 5: Commit**

```bash
git add test-extension/runtime/pom.xml test-extension/runtime/src/main/java/io/quarkiverse/temporal/test/MockTestWorkflowResetCallback.java test-extension/runtime/src/main/resources/META-INF/services/io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback test-extension/runtime/src/main/resources/META-INF/services/io.quarkus.test.junit.callback.QuarkusTestAfterEachCallback
git commit -m "feat: reset the mock TestWorkflowEnvironment before/after every test method"
```

---

### Task 6: Regression proof (`integration-tests`)

**Files:**
- Create: `integration-tests/src/main/java/io/quarkiverse/temporal/it/freshness/defaultWorker/FreshnessWorkflow.java`
- Create: `integration-tests/src/main/java/io/quarkiverse/temporal/it/freshness/defaultWorker/FreshnessWorkflowImpl.java`
- Create: `integration-tests/src/main/java/io/quarkiverse/temporal/it/freshness/FreshnessClientHolder.java`
- Create: `integration-tests/src/test/java/io/quarkiverse/temporal/it/TestWorkflowEnvironmentFreshnessIT.java`

**Interfaces:**
- Consumes: the fix from Tasks 1-5, exercised through ordinary `@Inject` — no direct code coupling, this task only proves behavior.

This module already depends on `quarkus-temporal-test` (test scope) and already has real `@QuarkusTest`/`*IT` classes (`CDIActivityIT`, `MoneyTransferIT`) using the module's default `application.properties` (`%test.quarkus.temporal.enable-mock=true`, `start-workers` defaulting to `true`). The new fixture is placed under a `defaultWorker` sub-package, mirroring the existing `it.cdi.defaultWorker`/`it.moneyTransfer.defaultWorker` convention that binds a workflow implementation to the extension's default worker - no new worker/task-queue config needed, and `start-workers=true` means the workflow gets auto-registered per test with no manual mock-activity setup required (this fixture needs no activities at all).

- [ ] **Step 1: Add the trivial fixture workflow**

```java
package io.quarkiverse.temporal.it.freshness.defaultWorker;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface FreshnessWorkflow {

    @WorkflowMethod
    String ping(String input);
}
```

```java
package io.quarkiverse.temporal.it.freshness.defaultWorker;

public class FreshnessWorkflowImpl implements FreshnessWorkflow {

    @Override
    public String ping(String input) {
        return "pong:" + input;
    }
}
```

- [ ] **Step 2: Add the application-code CDI bean that proves the fix isn't limited to the test class's own fields**

```java
package io.quarkiverse.temporal.it.freshness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.temporal.client.WorkflowClient;

/**
 * Stands in for ordinary application code (a REST resource, a service) that injects
 * {@link WorkflowClient} once and is never reconstructed per test - unlike the test class
 * itself, which JUnit reconstructs for every test method.
 */
@ApplicationScoped
public class FreshnessClientHolder {

    @Inject
    WorkflowClient workflowClient;

    public WorkflowClient get() {
        return workflowClient;
    }
}
```

- [ ] **Step 3: Write the regression test**

```java
package io.quarkiverse.temporal.it;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkiverse.temporal.it.freshness.FreshnessClientHolder;
import io.quarkiverse.temporal.it.freshness.defaultWorker.FreshnessWorkflow;
import io.quarkus.test.junit.QuarkusTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestWorkflowEnvironmentFreshnessIT {

    @Inject
    TestWorkflowEnvironment testEnv;

    @Inject
    FreshnessClientHolder applicationBean;

    @Test
    @Order(1)
    public void firstTestRunsAndClosesTheEnvironment() {
        runAndClose("first");
    }

    @Test
    @Order(2)
    public void secondTestGetsAFreshEnvironment() {
        // Before the fix, this method received the same, already-closed
        // TestWorkflowEnvironment as the first test, and testEnv.getWorkerFactory()
        // below would already be shut down.
        runAndClose("second");
    }

    private void runAndClose(String input) {
        // Uses the client injected into an application-code-style CDI bean (not the test
        // class's own field) to prove the fix isn't limited to the test class's fields.
        // If this client were stale, newWorkflowStub/ping below would fail or hang against
        // a different (or already-closed) in-memory environment.
        WorkflowClient current = applicationBean.get();
        FreshnessWorkflow workflow = current.newWorkflowStub(FreshnessWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(DEFAULT_WORKER_NAME).build());
        try {
            assertEquals("pong:" + input, workflow.ping(input));
        } finally {
            testEnv.getWorkerFactory().shutdown();
            testEnv.close();
        }
    }
}
```

**Discovered while implementing this task:** the original `assertSame(testEnv.getWorkflowClient(), applicationBean.get())` was a broken assertion, not a real check - `applicationBean.get()` returns a CDI client proxy (a stable, synthetic wrapper object), while `testEnv.getWorkflowClient()` returns the real underlying SDK object directly; the two are never reference-equal by design, regardless of whether the fix works. Removed it - the workflow call succeeding through `applicationBean.get()` is itself the meaningful proof (a stale client would be wired to a different, or already-closed, in-memory environment and the call would fail or hang).

- [ ] **Step 4: Run the integration test suite**

Run: `mvn -q verify -pl integration-tests -am`
Expected: BUILD SUCCESS, both `testWorkflowEnvironmentFreshnessIT` methods pass. If this plan were executed against the *old* (pre-fix) code, `secondTestGetsAFreshEnvironment` would fail with an error indicating the worker factory/environment was already shut down - that's the regression this proves.

- [ ] **Step 5: Commit**

```bash
git add integration-tests/src/main/java/io/quarkiverse/temporal/it/freshness integration-tests/src/test/java/io/quarkiverse/temporal/it/TestWorkflowEnvironmentFreshnessIT.java
git commit -m "test: prove TestWorkflowEnvironment/WorkflowClient are fresh per test method"
```

---

### Task 7: Full suite verification, and a quick look at the disabled tests

**Files:**
- Investigate only (no guaranteed changes): `test-extension/deployment/src/test/java/io/quarkiverse/temporal/test/deployment/StartWorkersEnabledTest.java`, `StartWorkersDisabledTest.java`

**Interfaces:** none - this task is verification, not new code.

- [ ] **Step 1: Run the full reactor test suite**

Run: `mvn -q verify`
Expected: BUILD SUCCESS across `extension`, `test-extension`, and `integration-tests` - including the pre-existing `CDIActivityIT`, `MoneyTransferIT`, `ContextPropagatorCdiIT`, `DataConverterCdiIT` (none of which explicitly close their environment, so they must keep working unchanged under the new fresh-per-test behavior).

- [ ] **Step 2: Look at why `StartWorkersEnabledTest`/`StartWorkersDisabledTest` are `@Disabled`**

These two tests (in `test-extension/deployment`) are `QuarkusUnitTest`-based and were disabled in commit `700eeff` ("custom client") with no explanation. Read them, try removing `@Disabled` locally, and run:

Run: `mvn -q -pl test-extension/deployment -am test -Dtest=StartWorkersEnabledTest,StartWorkersDisabledTest`

If they pass cleanly after this change, remove the `@Disabled` annotations and commit that as a small separate cleanup. If they still fail for a reason unrelated to this fix, leave them disabled - this is a nice-to-have, not a requirement of this plan.

- [ ] **Step 3: Final commit (only if Step 2 produced a change)**

```bash
git add test-extension/deployment/src/test/java/io/quarkiverse/temporal/test/deployment/StartWorkersEnabledTest.java test-extension/deployment/src/test/java/io/quarkiverse/temporal/test/deployment/StartWorkersDisabledTest.java
git commit -m "test: re-enable StartWorkersEnabledTest/StartWorkersDisabledTest"
```
