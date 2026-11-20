### Defining workflows and activities

- Workflows and activities are discovered automatically: implement an interface annotated with `@WorkflowInterface` or `@ActivityInterface`. Do not register them by hand.
- Without further configuration an implementation is bound to the default worker. Bind it to named workers with `@TemporalWorkflow(workers = "...")` / `@TemporalActivity(workers = "...")`, or with `quarkus.temporal.worker.<name>.workflow-classes[0]` / `activity-classes[0]` when you do not control the class.
- Setting `workflow-classes` / `activity-classes` on a worker removes the automatic binding to the default worker. Bind to the default worker explicitly too if it is still needed.
- A worker can hold at most one implementation of a given workflow or activity interface.
- If only the interface is in the project, annotate the interface with `@TemporalWorkflow` to say which workers it belongs to.

### Dependency injection

- Activity implementations are CDI beans: `@Inject` works in them.
- Workflow implementations are NOT CDI beans. Never inject services into a workflow: it breaks replay and causes `NonDeterministicException`. Pass configuration through a Local Activity instead.
- The one exception is activity stubs: `@TemporalActivityStub(startToCloseTimeout = "10s")` on a field of a workflow implementation (durations use Quarkus syntax: `10s`, `5m`, `PT1H`). Use `local = true` for local activities.
- `@TemporalActivityStub` fields must be `@ActivityInterface` types (checked at build time). With a `@WorkflowInit` constructor, the stubs are available after the constructor returns, not inside it. The workflow must implement a single `@WorkflowInterface`.
- `WorkflowClient` is a CDI bean. Inject workflow stubs with `@Inject @TemporalWorkflowStub(workflowId = "...")`. If the workflow is bound to several workers, set `worker = "..."`.

### Pitfalls

- `TemporalInstance<T>` is a factory. `instance.workflowId("id")` returns a NEW stub and does not modify the instance. Always use the returned stub. Passing the `TemporalInstance` to `WorkflowStub.fromTyped(...)` fails with `arguments must be created through WorkflowClient.newWorkflowStub`.
- `quarkus.temporal.workflow.*` options only apply to stubs created by the extension (`@TemporalWorkflowStub`). Stubs built by hand with `client.newWorkflowStub(...)` ignore them.
- There is no `quarkus.temporal.workflow.task-queue`. An injected stub uses the task queue of the worker it is bound to: the worker name (`<default>` for the default worker), overridable with `quarkus.temporal.worker.task-queue` (default worker) or `quarkus.temporal.worker.<name>.task-queue`.
- For `signalWithStart`, unwrap an injected stub: `WorkflowStub.fromTyped(instance.workflowId(id)).signalWithStart(...)`.
- Invalid combinations of `workflow-id-conflict-policy` and `workflow-id-reuse-policy` throw a `ConfigurationException` when the stub is created.
- By default workers start with the application and startup fails if Temporal is unreachable. For resilience set `quarkus.temporal.worker-factory.fail-on-startup-error=false` and, to keep retrying in the background, `startup-background-retry-enabled=true` (`startup-max-attempts` is then ignored).
- Interceptors (`WorkflowClientInterceptor`, `WorkerInterceptor`), `ContextPropagator` and `DataConverter` are plugged in by producing a CDI bean (`@Unremovable` for beans nothing else injects). Order interceptors with `@Priority`.

### Dev Services and the Web UI

- Dev Services starts a `temporalio/temporal` container in dev and test mode when `quarkus.temporal.connection.target` is not set. Setting a target takes precedence.
- The Temporal Web UI runs in the same container, with a random host port. Open it from the Temporal card in the Quarkus Dev UI (`/q/dev-ui`).
- In dev mode the container is shared between applications with the same `quarkus.temporal.devservices.service-name`. In test mode it is not shared.

### Testing

- Prefer `@QuarkusTest` with Dev Services (a real Temporal dev server in a container) for end-to-end checks.
- For fast tests without a container add the `io.quarkiverse.temporal:quarkus-temporal-test` extension (test scope) and set:

```properties
%test.quarkus.temporal.enable-mock=true
%test.quarkus.temporal.start-workers=false
%test.quarkus.devservices.enabled=false
```

- `quarkus.temporal.enable-mock` is build-time. Enabling it without `quarkus-temporal-test` on the classpath fails the build.
- In mock mode `WorkflowClient`, `WorkerFactory` and `TestWorkflowEnvironment` are backed by Temporal's in-memory test server. Inject `TestWorkflowEnvironment` for time skipping.
- With `start-workers=false`, activities are NOT registered automatically. Register every activity the workflow calls (mock or real bean) before starting the `WorkerFactory`.
- Extension build-time validation is tested with `QuarkusUnitTest`. Set `quarkus.temporal.start-workers: false` in its `application.properties` when no server is available.

### Native

- Native image is supported. Do not add gRPC or Netty reflection or substitution config in the application: the extension provides it.
