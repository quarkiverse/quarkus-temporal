package fr.lavachequicode.temporal.plugin;

import java.util.List;

import jakarta.enterprise.inject.spi.CDI;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

@Recorder
public class WorkerFactoryRecorder {

    public RuntimeValue<WorkerFactory> createWorkerFactory(WorkflowClient workflowClient) {
        return new RuntimeValue<>(WorkerFactory.newInstance(workflowClient));
    }

    public void createWorker(RuntimeValue<WorkerFactory> runtimeValue, List<Class<?>> workflows, List<Class<?>> activities) {
        WorkerFactory workerFactory = runtimeValue.getValue();
        Worker worker = workerFactory.newWorker("MONEY_TRANSFER_TASK_QUEUE");
        for (var workflow : workflows) {
            worker.registerWorkflowImplementationTypes(workflow);
        }
        for (var activity : activities) {
            worker.registerActivitiesImplementations(CDI.current().select(activity).get());
        }

    }

    public void startWorkerFactory(ShutdownContext shutdownContext, RuntimeValue<WorkerFactory> runtimeValue) {
        WorkerFactory workerFactory = runtimeValue.getValue();
        workerFactory.start();
        shutdownContext.addShutdownTask(workerFactory::shutdown);
    }

}
