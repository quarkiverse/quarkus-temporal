# Per-Test-Fresh Mock TestWorkflowEnvironment — Design

## Problem

PR [#233](https://github.com/quarkiverse/quarkus-temporal/pull/233) (author: michael-read) demonstrates that `TestWorkflowEnvironment`, `WorkflowClient`, and `WorkerFactory` — the beans produced by `quarkus-temporal-test` when `quarkus.temporal.enable-mock=true` — are registered as `@Singleton`-scoped CDI synthetic beans, created once at `RUNTIME_INIT`. Quarkus reuses the same application instance across all `@QuarkusTest` classes in a JVM run, so every test method in every test class receives the *same* `TestWorkflowEnvironment` instance.

This is fatal for any test suite with more than one test method that exercises a workflow to completion: `TestWorkflowEnvironment` is a single-use, stateful in-memory Temporal server. A test that calls `testEnv.close()` (a normal cleanup step, and the pattern used throughout the `unit-tests` module added in PR #233) leaves every subsequent test with a closed, unusable environment. Temporal's own `TestWorkflowExtension` (a JUnit5 `BeforeEachCallback`) avoids this by constructing a brand-new `TestWorkflowEnvironment` per test method — this is the difference PR #233's `OrderEntityWorkflowMultiTestSuccessTest` demonstrates by bypassing the extension's injected beans entirely and using the Temporal Test Kit directly.

Goal: make the extension-provided `TestWorkflowEnvironment`/`WorkflowClient`/`WorkerFactory` behave the same way — fresh per test method — without requiring any change to user test code (no `@RegisterExtension` boilerplate), and without losing the extension's auto-discovery of workflow implementation types/workers.

## Constraints discovered during investigation

1. **Worker/workflow-type registration is also a one-time boot step.** `TemporalProcessor.setupWorkerFactory` (a `@Record(RUNTIME_INIT)` build step) calls `WorkerFactoryRecorder.createWorker(name, workflows, activities)` once per declared worker, against whatever `WorkerFactory` bean exists at that moment. A naive "swap the environment" fix would leave every subsequent test's fresh `WorkerFactory` with zero registered workers/workflow types.
2. **`io.temporal.worker.WorkerFactory` is a `final` class with only a `private` 2-argument constructor** (`WorkerFactory(WorkflowClient, WorkerFactoryOptions)`, verified via `javap`), and no no-arg constructor. `TestWorkflowEnvironment` and `WorkflowClient` are interfaces.
3. **CDI field injection into a `@QuarkusTest` instance happens at test-instance construction time** (`QuarkusTestExtension.interceptTestClassConstructor`), which runs *before* any `QuarkusTestBeforeEachCallback`/`QuarkusTestAfterConstructCallback` fires. A plain re-assignment of a bean's backing value in a `BeforeEachCallback` would be too late to affect an already-injected raw field reference — unless the injected reference is a *proxy* whose delegate can be swapped after the fact, or unless the fresh value is already in place *before* that construction happens.
4. Quarkus already solves the proxy-swap half of (3) elsewhere: `io.quarkus.test.junit.QuarkusMock` (`installMockForType`/`installMockForInstance`) swaps what a **normal-scoped** bean's client proxy delegates to, for the duration of one test, and this is exactly how `@InjectMock`/Mockito support works internally (`SetMockitoMockAsBeanMockCallback`, a `QuarkusTestBeforeEachCallback`, uses it). `QuarkusTestExtension.beforeEach()` calls `pushMockContext()` *before* invoking `QuarkusTestBeforeEachCallback`s, and `afterEach()` calls `popMockContext()` after ours — so mock installs made inside our callback are automatically test-scoped with no manual cleanup needed. This works cleanly for `TestWorkflowEnvironment`/`WorkflowClient` (interfaces).
5. **`WorkerFactory` cannot use the same proxy mechanism.** CDI/ArC's client-proxy generator (`io.quarkus.arc.processor.ClientProxyGenerator`) subclasses the bean's own concrete class when it isn't an interface, and its generated constructor calls `invokeSpecial(ConstructorDesc.of(superClass), ...)` — a **no-arg** super constructor call. `WorkerFactory` has no no-arg constructor, and its only constructor is `private` (uncallable even from hand-written subclassing code). Quarkus's usual trick for a merely-`final` third-party class (`BytecodeTransformerBuildItem` stripping `ACC_FINAL`, as seen in `quarkus-grpc`'s `FieldDefinalizingVisitor`) does not solve this — it only addresses the `final` flag, not the missing/private constructor. Synthesizing a working no-arg constructor via ASM was considered and rejected as too invasive/risky for this change (see Approaches below).
6. `WorkflowClient`/`WorkerFactory` in mock mode aren't only consumed by test classes — they're the *same* beans application code under test (REST resources, services) injects. A fix limited to patching the test class's own fields (rejected alternative, see below) would leave application code stale after the first test. In practice `WorkflowClient` is the far more common injection point for application business logic; `WorkerFactory` is primarily a test/infrastructure concern.

## Approaches considered

**Rejected: reflection-based field patching on the test class only.** A `QuarkusTestAfterConstructCallback` could reflectively overwrite `@Inject`-annotated fields of the three types directly on the test instance (the same technique `@InjectMock` uses for its final field-set step, minus the CDI-wide override). Simpler, no bytecode transform needed — but only fixes the test class's own fields. Any application bean that also injects `WorkflowClient`/`WorkerFactory` (the extension's core mock-mode use case) stays bound to a stale instance after the first test. Rejected because it doesn't fully fix the reported problem class.

**Rejected: ASM-synthesized no-arg constructor on `WorkerFactory` to unblock full CDI proxying.** Would let all three beans use the same clean `ApplicationScoped` + `QuarkusMock` mechanism uniformly. Rejected as too risky for this change: it requires injecting a brand-new constructor into third-party SDK bytecode that leaves the real (final) fields unassigned — verifiable at the JVM level, but unvalidated in this codebase and a meaningfully bigger surface for subtle breakage than the chosen approach, for a benefit (freshness when `WorkerFactory` is injected into a non-per-test-reconstructed application bean) that covers an uncommon case.

**Chosen: hybrid — `QuarkusMock` proxy swap for `TestWorkflowEnvironment`/`WorkflowClient`, "prepare-ahead" `@Dependent` scope for `WorkerFactory`.** `TestWorkflowEnvironment` and `WorkflowClient` become `ApplicationScoped` and are swapped per test via `QuarkusMock`, exactly as originally designed. `WorkerFactory` instead becomes `@Dependent`-scoped, with its producer reading "whatever is currently prepared" from a plain holder — no proxy needed, so the constructor problem never arises. The timing gap this creates (CDI resolves `@Dependent` fields at test-construction time, *before* `beforeEach` can run) is closed by preparing test N+1's environment during test N's `afterEach`, not test N+1's `beforeEach` — so by the time the next test is constructed and its `@Dependent WorkerFactory` field resolves, the fresh instance is already sitting in the holder. `beforeEach` then installs that *same* already-prepared instance into `QuarkusMock` for the other two beans, keeping all three identity-consistent. This fully fixes the reported bug (`WorkerFactory` injected directly into the `@QuarkusTest` class, which is reconstructed per test method) and fully fixes `WorkflowClient` freshness everywhere, including application code under test. It leaves one narrow, documented gap: an `@ApplicationScoped` *production* bean that injects `WorkerFactory` directly (not the test class, not another per-test-reconstructed bean) would still see only the boot-time instance.

## Design

### Components

| Component | Module | Change |
|---|---|---|
| `TestWorkflowEnvironment` synthetic bean | `test-extension/deployment` | Scope `Singleton` → `ApplicationScoped`. (`WorkflowClient`'s bean is already `ApplicationScoped` today — no scope change needed there, just the new `QuarkusMock` install.) |
| `WorkerFactory` synthetic bean | `test-extension/deployment` | Scope `Singleton` → `Dependent`. `createWith` reads `MockTestEnvironmentHolder.current().getWorkerFactory()` instead of building anything itself. |
| `MockTestEnvironmentHolder` (new) | `test-extension/runtime` | Plain static holder (not a CDI bean) storing the "currently prepared" `TestWorkflowEnvironment` for the in-progress or upcoming test. |
| `WorkerRegistrationRegistry` (new) | `extension/runtime` | Static registry of replay closures: `(name, workflows, activities) -> WorkerFactoryRecorder.doCreateWorker(...)`. Populated unconditionally (cheap — a handful of class references), read only by `test-extension`. |
| `WorkerFactoryRecorder.createWorker(...)` | `extension/runtime` | Existing logic extracted into a `doCreateWorker(...)` core method; the public `createWorker(...)` (called once at boot by `TemporalProcessor.setupWorkerFactory`, and replayed later by `test-extension`) now also appends a replay closure to `WorkerRegistrationRegistry` before delegating to `doCreateWorker`. |
| `MockTestWorkflowResetCallback` (new) | `test-extension/runtime` | Implements both `QuarkusTestBeforeEachCallback` and `QuarkusTestAfterEachCallback`. See lifecycle below. |
| `test-extension/runtime` `pom.xml` | `test-extension/runtime` | New dependency: `io.quarkus:quarkus-junit5` (compile scope — this module is already test-classpath-only by design). |
| `META-INF/services/io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback` and `...QuarkusTestAfterEachCallback` | `test-extension/runtime` | New service registrations for auto-discovery — zero user test-code changes required. |

No changes to the production (non-mock) code paths in `extension`. The production `WorkerFactory` synthetic bean (`extension/deployment`, `onlyIfNot EnableMock`) is untouched. No bytecode transforms anywhere in this design.

### Per-test lifecycle

**App boot (once, `RUNTIME_INIT`):**
1. `TestWorkflowRecorder.createTestWorkflowEnvironment()` builds the initial `TestWorkflowEnvironment` via `TestEnvironmentOptions` (unchanged construction logic), and now also seeds it into `MockTestEnvironmentHolder` so it's available the moment anything (including the `@Dependent WorkerFactory` producer) asks for "current".
2. `TemporalProcessor.setupWorkerFactory` calls `WorkerFactoryRecorder.createWorker(name, workflows, activities)` per declared worker — unchanged behavior, now also records a replay closure. Since `WorkerFactory` now resolves via the holder, this registers against the same instance just seeded in step 1.
3. If `quarkus.temporal.start-workers=true`, the existing `startWorkers` build step starts the boot-time `WorkerFactory` (unchanged).

**Before each `@Test` method — `MockTestWorkflowResetCallback.beforeEach(QuarkusTestMethodContext ctx)`:**
1. Read `MockTestEnvironmentHolder.current()` — already prepared, either by app boot (test #1) or by the *previous* test's `afterEach` (test #2+). Do not build anything new here.
2. `QuarkusMock.installMockForType(current, TestWorkflowEnvironment.class)`.
3. `QuarkusMock.installMockForType(current.getWorkflowClient(), WorkflowClient.class)`.
4. Track `current` on the callback instance as "the environment in use for this test" so `afterEach` tears down the right one.

(No `QuarkusMock` install needed for `WorkerFactory` — as a `@Dependent` bean it was already resolved fresh, from the same holder, at this test's construction time, before `beforeEach` even ran.)

**After each `@Test` method — `MockTestWorkflowResetCallback.afterEach(...)`:**
1. Tear down the environment tracked in step 4 above: `inUse.getWorkerFactory().shutdown()` (guarded), then `inUse.close()` (guarded) — a test that already closed things manually in its own `finally` block (like the existing `Failure1Test`/`Failure2Test` patterns) must not fail this step.
2. Prepare the *next* one: build a fresh `TestWorkflowEnvironment` (same `TestEnvironmentOptions` logic as boot), replay every entry in `WorkerRegistrationRegistry` against its `getWorkerFactory()` — each closure calls `WorkerFactoryRecorder.doCreateWorker(...)`, which resolves `WorkerFactory` via `CDI.current().select(WorkerFactory.class).get()`, which (as `@Dependent`) reads the holder and gets this new instance. If `start-workers=true`, call `freshWorkerFactory.start()` directly (no retry/background-retry machinery — that exists for real gRPC connectivity, not an in-memory test double); if `false` (the common mock-testing case, as used throughout PR #233's `unit-tests` module), leave it unstarted so the next test can register mocked activities and call `testEnv.start()` itself.
3. Store the freshly-prepared instance via `MockTestEnvironmentHolder.set(...)`.

One bounded loose end: after the *last* test in a run, this still prepares one more environment that's never consumed or explicitly torn down — reclaimed at JVM exit. Acceptable for an in-memory test double; not worth a `QuarkusTestAfterAllCallback` for this change.

### Error handling

- Teardown failures in `afterEach` step 1 are logged (`debug`) and swallowed, not propagated — see above.
- Replay/prepare failures in `afterEach` step 2 (e.g. a workflow implementation that can't be instantiated) propagate normally, failing whichever test triggered them — matching how a boot-time registration failure fails app startup today.
- `QuarkusMock.installMockForType` failures (e.g. a bean unexpectedly not normal-scoped) are not caught — that would indicate an extension bug, not a user-facing condition.

## Testing plan

**Important mechanism note discovered during implementation planning:** `test-extension/deployment`'s existing test suite (`MockEnabledTest`, `StartWorkersEnabledTest`, etc.) uses `io.quarkus.test.QuarkusUnitTest`, a lighter-weight harness for testing build steps in-process. Its `beforeEach`/`afterEach` do **not** invoke the `QuarkusTestBeforeEachCallback`/`QuarkusTestAfterEachCallback` SPI at all (confirmed by reading its source) — so this fix's callback would never fire in a `QuarkusUnitTest`-based test, and such a test would prove nothing about it. The regression/behavioral tests for this fix must instead live in the `integration-tests` module, which already depends on `quarkus-temporal-test` and already has real `@QuarkusTest` classes (`CDIActivityIT`, `MoneyTransferIT`) that go through the full `QuarkusTestExtension` lifecycle, including our callback.

1. **Regression proof** (new, in `integration-tests`): a `@QuarkusTest` IT class with two `@Order`ed test methods against a small new trivial workflow (no activities needed — `integration-tests`' default `start-workers=true` auto-registers it). Each method runs the workflow, then explicitly closes it (`workerFactory.shutdown()` + `testEnv.close()`) in a `finally` block — reproducing PR #233's exact `Failure2Test` pattern. Before the fix, the second method would fail immediately (stale, already-closed environment); after the fix, it gets a working fresh one.
2. **Application-code freshness**, folded into the same IT class: a small `@ApplicationScoped` CDI bean (not the test class) injects `WorkflowClient`; each test method asserts it resolves to the *current* test's client (`== testEnv.getWorkflowClient()`), proving the fix isn't limited to the test class's own fields.
3. Investigate why the existing `test-extension/deployment` `StartWorkersEnabledTest`/`StartWorkersDisabledTest` are currently `@Disabled` (no explanation in history) — separate from this fix, worth a quick look, not a hard requirement.
4. Full existing `extension` + `test-extension` + `integration-tests` suite must stay green — this change must not alter production (non-mock) behavior, and existing IT classes (which don't explicitly close the environment) must keep working with a fresh-per-test environment.

## Open questions / risks

- The "prepare-ahead" timing (build test N+1's environment during test N's `afterEach`) is a real but slightly unusual pattern; it must be implemented carefully so the very first test (prepared at boot, not in an `afterEach`) and the very last test (whose prepared successor is never consumed) both behave correctly — covered explicitly in the lifecycle and testing sections above.
- Documented, accepted gap: an `@ApplicationScoped` *production* bean (not the test class, not another per-test-reconstructed bean) that injects `WorkerFactory` directly still only ever sees the boot-time instance. Worth a line in the extension's testing docs.
- `unit-tests` (PR #233's demonstration module) is currently a standalone proof-of-concept the PR author suggested *not* merging as-is ("my preference would be not to merge this into the project but just to update the docs"). This design assumes we adapt its test classes into the extension's own test suite rather than merging the module verbatim — worth confirming with the PR author before finalizing which files move where.
