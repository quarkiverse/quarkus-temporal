# Per-Test-Fresh Mock TestWorkflowEnvironment — Design

## Problem

PR [#233](https://github.com/quarkiverse/quarkus-temporal/pull/233) (author: michael-read) demonstrates that `TestWorkflowEnvironment`, `WorkflowClient`, and `WorkerFactory` — the beans produced by `quarkus-temporal-test` when `quarkus.temporal.enable-mock=true` — are registered as `@Singleton`-scoped CDI synthetic beans, created once at `RUNTIME_INIT`. Quarkus reuses the same application instance across all `@QuarkusTest` classes in a JVM run, so every test method in every test class receives the *same* `TestWorkflowEnvironment` instance.

This is fatal for any test suite with more than one test method that exercises a workflow to completion: `TestWorkflowEnvironment` is a single-use, stateful in-memory Temporal server. A test that calls `testEnv.close()` (a normal cleanup step, and the pattern used throughout the `unit-tests` module added in PR #233) leaves every subsequent test with a closed, unusable environment. Temporal's own `TestWorkflowExtension` (a JUnit5 `BeforeEachCallback`) avoids this by constructing a brand-new `TestWorkflowEnvironment` per test method — this is the difference PR #233's `OrderEntityWorkflowMultiTestSuccessTest` demonstrates by bypassing the extension's injected beans entirely and using the Temporal Test Kit directly.

Goal: make the extension-provided `TestWorkflowEnvironment`/`WorkflowClient`/`WorkerFactory` behave the same way — fresh per test method — without requiring any change to user test code (no `@RegisterExtension` boilerplate), and without losing the extension's auto-discovery of workflow implementation types/workers.

## Constraints discovered during investigation

1. **Worker/workflow-type registration is also a one-time boot step.** `TemporalProcessor.setupWorkerFactory` (a `@Record(RUNTIME_INIT)` build step) calls `WorkerFactoryRecorder.createWorker(name, workflows, activities)` once per declared worker, against whatever `WorkerFactory` bean exists at that moment. A naive "swap the environment" fix would leave every subsequent test's fresh `WorkerFactory` with zero registered workers/workflow types.
2. **`io.temporal.worker.WorkerFactory` is a `final` class.** CDI/ArC cannot generate a normal-scope client proxy for a final class (subclassing is required and impossible). `TestWorkflowEnvironment` and `WorkflowClient` are interfaces and are proxyable.
3. **CDI field injection into a `@QuarkusTest` instance happens at test-instance construction time** (`QuarkusTestExtension.interceptTestClassConstructor`), which runs *before* any `QuarkusTestBeforeEachCallback`/`QuarkusTestAfterConstructCallback` fires. A plain re-assignment of a bean's backing value in a `BeforeEachCallback` would be too late to affect an already-injected raw field reference — unless the injected reference is a *proxy* whose delegate can be swapped after the fact.
4. Quarkus already solves both (2) and (3) elsewhere: `io.quarkus.test.junit.QuarkusMock` (`installMockForType`/`installMockForInstance`) swaps what a **normal-scoped** bean's client proxy delegates to, for the duration of one test, and this is exactly how `@InjectMock`/Mockito support works internally (`SetMockitoMockAsBeanMockCallback`, a `QuarkusTestBeforeEachCallback`, uses it). `QuarkusTestExtension.beforeEach()` calls `pushMockContext()` *before* invoking `QuarkusTestBeforeEachCallback`s, and `afterEach()` calls `popMockContext()` after ours — so mock installs made inside our callback are automatically test-scoped with no manual cleanup needed.
5. Quarkus's standard technique for proxying a third-party `final` class is a `BytecodeTransformerBuildItem` that strips the `final` flag at class-load time within the (test) application classloader. This doesn't touch the original jar and is scoped to this build only.
6. `WorkflowClient`/`WorkerFactory` in mock mode aren't only consumed by test classes — they're the *same* beans application code under test (REST resources, services) injects. A fix limited to patching the test class's own fields (rejected alternative, see below) would leave application code stale after the first test.

## Approaches considered

**Rejected: reflection-based field patching on the test class only.** A `QuarkusTestAfterConstructCallback` could reflectively overwrite `@Inject`-annotated fields of the three types directly on the test instance (the same technique `@InjectMock` uses for its final field-set step, minus the CDI-wide override). Simpler, no bytecode transform needed — but only fixes the test class's own fields. Any application bean that also injects `WorkflowClient`/`WorkerFactory` (the extension's core mock-mode use case) stays bound to a stale instance after the first test. Rejected because it doesn't fully fix the reported problem class.

**Chosen: CDI normal-scope proxy swap via `QuarkusMock`, with `WorkerFactory` de-finalized via bytecode transform.** More code, touches both `extension/runtime` and `test-extension`, but is the complete, general fix: it corrects staleness for the test class *and* for application code under test, using the same mechanism Quarkus's own mocking support relies on.

## Design

### Components

| Component | Module | Change |
|---|---|---|
| `TestWorkflowEnvironment`, `WorkflowClient`, `WorkerFactory` synthetic beans | `test-extension/deployment` | Scope `Singleton` → `ApplicationScoped`. Creation logic (`createWith`) unchanged — still builds the initial instance at boot. |
| New `BuildStep` producing `BytecodeTransformerBuildItem` | `test-extension/deployment` | Strips `final` off `io.temporal.worker.WorkerFactory`'s class bytecode, gated `onlyIf = TemporalProcessor.EnableMock`. No effect on production (non-mock) builds. |
| `WorkerRegistrationRegistry` (new) | `extension/runtime` | Static registry of replay closures: `(name, workflows, activities) -> WorkerFactoryRecorder.doCreateWorker(...)`. Populated unconditionally (cheap — a handful of class references), read only by `test-extension`. |
| `WorkerFactoryRecorder.createWorker(...)` | `extension/runtime` | Existing logic extracted into a `doCreateWorker(...)` core method; the public `createWorker(...)` (called once at boot by `TemporalProcessor.setupWorkerFactory`) now also appends a replay closure to `WorkerRegistrationRegistry` before delegating to `doCreateWorker`. |
| `MockTestWorkflowResetCallback` (new) | `test-extension/runtime` | Implements both `QuarkusTestBeforeEachCallback` and `QuarkusTestAfterEachCallback`. See lifecycle below. |
| `test-extension/runtime` `pom.xml` | `test-extension/runtime` | New dependency: `io.quarkus:quarkus-junit5` (compile scope — this module is already test-classpath-only by design). |
| `META-INF/services/io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback` and `...QuarkusTestAfterEachCallback` | `test-extension/runtime` | New service registrations for auto-discovery — zero user test-code changes required. |

No changes to the production (non-mock) code paths in `extension`. The production `WorkerFactory` synthetic bean (`extension/deployment`, `onlyIfNot EnableMock`) is untouched.

### Per-test lifecycle

**App boot (once, `RUNTIME_INIT` — unchanged except bean scope):**
1. `TestWorkflowRecorder.createTestWorkflowEnvironment()` builds the initial `TestWorkflowEnvironment` via `TestEnvironmentOptions` (unchanged logic).
2. `TemporalProcessor.setupWorkerFactory` calls `WorkerFactoryRecorder.createWorker(name, workflows, activities)` per declared worker — unchanged behavior, now also records a replay closure.
3. If `quarkus.temporal.start-workers=true`, the existing `startWorkers` build step starts the boot-time `WorkerFactory` (unchanged).

**Before each `@Test` method — `MockTestWorkflowResetCallback.beforeEach(QuarkusTestMethodContext ctx)`:**
1. Build a fresh `TestWorkflowEnvironment` (same `TestEnvironmentOptions` construction logic as `TestWorkflowRecorder`, extracted to a shared method).
2. `QuarkusMock.installMockForType(freshEnv, TestWorkflowEnvironment.class)`.
3. `QuarkusMock.installMockForType(freshEnv.getWorkflowClient(), WorkflowClient.class)`.
4. `QuarkusMock.installMockForType(freshEnv.getWorkerFactory(), WorkerFactory.class)`.
5. Replay every entry in `WorkerRegistrationRegistry` — each closure calls `WorkerFactoryRecorder.doCreateWorker(...)`, which resolves `WorkerFactory` via `CDI.current().select(WorkerFactory.class).get()`. Because that now returns the proxy installed in step 4, workers and workflow implementation types are recreated against the fresh factory exactly as they were at boot.
6. If `start-workers=true`, call `freshWorkerFactory.start()` directly (no retry/background-retry machinery — that exists for real gRPC connectivity, not an in-memory test double). If `false` (the common mock-testing case, as used throughout PR #233's `unit-tests` module), leave it unstarted so the test can register mocked activities and call `testEnv.start()` itself.
7. Track the fresh `TestWorkflowEnvironment`/`WorkerFactory` on the callback instance so `afterEach` closes the right one.

**After each `@Test` method — `MockTestWorkflowResetCallback.afterEach(...)`:**
1. `workerFactory.shutdown()`, guarded (log at `debug` and continue on failure — a test that already shut it down manually in its own `finally` block must not fail the next test's setup).
2. `testEnv.close()`, same guard.
3. Clear the tracked instance.

`QuarkusTestExtension.afterEach()`'s own `popMockContext()` clears the `QuarkusMock` overrides automatically — no extra cleanup needed for that part.

### Error handling

- Teardown failures in `afterEach` are logged and swallowed (not propagated) — see above.
- Replay failures in `beforeEach` (e.g. a workflow implementation that can't be instantiated) propagate normally, failing that test method — matching how a boot-time registration failure fails app startup today.
- `QuarkusMock.installMockForType` failures (e.g. a bean unexpectedly not normal-scoped) are not caught — that would indicate an extension bug, not a user-facing condition.
- The `BytecodeTransformerBuildItem` is `onlyIf = EnableMock`, so it never runs for production builds.

## Testing plan

1. **Regression proof**: bring in `OrderEntityWorkflowMultiTestFailure1Test` and `OrderEntityWorkflowMultiTestFailure2Test` from PR #233 (renamed appropriately) and confirm both test methods in each class pass when run together — the direct proof this fixes the reported bug.
2. **`test-extension/deployment` unit tests** (`QuarkusUnitTest`-based, matching existing module style):
   - Multiple `@Test` methods directly injecting `TestWorkflowEnvironment`/`WorkflowClient`/`WorkerFactory`, asserting the instances differ across methods and that one test's workflow history is invisible to the next.
   - `start-workers=true`: assert the fresh factory is auto-started each test. Investigate why the existing `StartWorkersEnabledTest`/`StartWorkersDisabledTest` are currently `@Disabled` (no explanation in history) and re-enable if this fix resolves whatever was blocking them — not a hard requirement of this change, but worth checking.
   - A case proving the *application-code* path: a plain `@ApplicationScoped` CDI bean (not the test class) injects `WorkflowClient`, and two test methods confirm it resolves the current per-test client each time.
3. Full existing `extension` + `test-extension` suite must stay green — this change must not alter production (non-mock) behavior.

## Open questions / risks

- The `BytecodeTransformerBuildItem` approach to de-finalizing `WorkerFactory` is a known Quarkus technique but hasn't been exercised in this codebase before; worth validating early in implementation that ArC accepts the transformed class for proxy generation without further tweaks (e.g. if any individual methods ArC needs to override are themselves `final`, though unlikely for a class that's only class-level `final`).
- `unit-tests` (PR #233's demonstration module) is currently a standalone proof-of-concept the PR author suggested *not* merging as-is ("my preference would be not to merge this into the project but just to update the docs"). This design assumes we adapt its test classes into the extension's own test suite rather than merging the module verbatim — worth confirming with the PR author before finalizing which files move where.
