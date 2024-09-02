package io.quarkiverse.temporal.deployment;

import static io.quarkiverse.temporal.TemporalWorkflowStub.DEFAULT_WORKER;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

import io.quarkiverse.temporal.TemporalWorkflowStub;
import io.quarkus.arc.processor.Annotations;
import io.quarkus.arc.processor.InjectionPointsTransformer;
import io.quarkus.runtime.configuration.ConfigurationException;

public class TemporalWorkflowStubTransformer implements InjectionPointsTransformer {

    public static DotName TEMPORAL_WORKFLOW_STUB = DotName.createSimple(TemporalWorkflowStub.class);

    final Map<DotName, String[]> workersByWorkflow = new HashMap<>();
    final IndexView index;

    TemporalWorkflowStubTransformer(IndexView index, List<WorkflowBuildItem> buildItems) {
        this.index = index;
        for (WorkflowBuildItem buildItem : buildItems) {
            workersByWorkflow.put(DotName.createSimple(buildItem.workflow), buildItem.workers);
        }
    }

    @Override
    public boolean appliesTo(Type requiredType) {
        return true;
    }

    @Override
    public void transform(TransformationContext ctx) {

        AnnotationInstance annotation = Annotations.find(ctx.getQualifiers(), TEMPORAL_WORKFLOW_STUB);
        AnnotationTarget target = ctx.getTarget();

        if (annotation == null) {
            return;
        }

        DotName type;

        if (target.kind() == AnnotationTarget.Kind.FIELD) {
            type = target.asField().type().name();
        } else if (target.kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
            type = target.asMethodParameter().type().name();
        } else {
            throw new IllegalStateException("invalid injection point type " + target.kind());
        }

        if (!workersByWorkflow.containsKey(type)) {
            return;
        }

        if (!DEFAULT_WORKER.equals(annotation.valueWithDefault(index, "worker").asString())) {
            return;
        }

        String[] workers = workersByWorkflow.get(type);

        if (workers.length != 1) {
            throw new ConfigurationException("the workflow " + type
                    + " is associated to more than one worker, worker parameter is required in the TemporalWorkflowStub annotation. ");
        }

        ctx.transform()
                .remove(ai -> TEMPORAL_WORKFLOW_STUB.equals(ai.name()))
                .add(AnnotationInstance.builder(annotation.name())
                        .addAll(annotation.values())
                        .add("worker", workers[0])
                        .build())
                .done();
    }
}
