# Unit Tests

The purpose of this sub-project is to demonstrate the difficulty of creating working unit tests using the `quarkus-temporal` test extension.

The basic problem is that whatever objects you inject into your tests using the `test-extension` are all singletons. This causes problems when more than one unit test is run concurrently, either across separate test files or within multiple tests in the same file.

I have created three test files, each containing the same two tests: a happy-path test and a not-so-happy-path test. The three test classes are:

1. `OrderEntityWorkflowMultiTestFailure1Test`
2. `OrderEntityWorkflowMultiTestFailure2Test`
3. `OrderEntityWorkflowMultiTestSuccessTest`

## `OrderEntityWorkflowMultiTestFailure1Test`

This test class tries to leverage the WorkflowClient and WorkerFactory singletons provided by the `test-extension`:

```java
@Inject
WorkflowClient workflowClient;

@Inject
WorkerFactory workerFactory;
```

The tests pass when run individually in your IDE or via Maven. However, when run concurrently, the second test method in the same class always fails.

## `OrderEntityWorkflowMultiTestFailure2Test`

This test class tries to leverage the `TestWorkflowEnvironment` provided by the `test-extension`:

```java
@Inject
TestWorkflowEnvironment testEnv;
```

Again, the tests pass when run individually in your IDE or via Maven. However, when run concurrently, the second test always fails.

## `OrderEntityWorkflowMultiTestSuccessTest`

This test bypasses the injected test object(s) provided by the `test-extension` and uses the Temporal Test Kit directly.

First, it registers the `TestWorkflowExtension` provided by Temporal:

```java
@RegisterExtension
public static final TestWorkflowExtension testWorkflowExtension =
        TestWorkflowExtension.newBuilder()
                .setWorkflowTypes(OrderEntityWorkflowImpl.class)
                .setDoNotStart(true)
                .build();
```

It is worth noting the registration of `OrderEntityWorkflowImpl.class`, as well as setting `setDoNotStart(true)`, which is needed so that you can register your mocked activities later in the test.

Next, a new instance of `TestWorkflowEnvironment` is created and injected into the test method parameters, along with an automatically created workflow stub:

```java
public void testHappyPathOrderProcessing(
        TestWorkflowEnvironment testEnv,
        OrderEntityWorkflow workflow
) {
    // ...
}
```

Because each test starts with a completely new test environment, all tests run successfully.

## Summary

The injectable objects provided by the `test-extension` are only useful for a single isolated test per project. If you need more than one test to exercise your complete workflow, the `test-extension` is unusable as it is today.

While Temporal favors integration testing with real Activities, I appreciate the Temporal Test Kit for mocking Activities and running multiple tests, because each test receives a new instance of `TestWorkflowEnvironment`.
