package io.quarkiverse.temporal;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKFLOW_GROUP_NAME;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

@Qualifier
@Retention(RUNTIME)
@Target({ METHOD, FIELD, PARAMETER, TYPE })
public @interface TemporalWorkflowStub {

    String DEFAULT_WORKER = "io.quarkiverse.temporal.TemporalWorkflowStub.worker.DEFAULT";

    String DEFAULT_WORKFLOW_ID = "io.quarkiverse.temporal.TemporalWorkflowStub.workflowId.DEFAULT";

    /**
     * Letting this blank will only work if the workflow is only bound to a single worker.
     * The default value does not necessary bind to the unnamed worker !
     */
    String worker() default DEFAULT_WORKER;

    /**
     * This allows to customize the workflow stub configuration.
     * Letting this blank will bind to the unnamed configuration group.
     */
    @Nonbinding
    String group() default DEFAULT_WORKFLOW_GROUP_NAME;

    @Nonbinding
    String workflowId() default DEFAULT_WORKFLOW_ID;

    class Literal extends AnnotationLiteral<TemporalWorkflowStub> implements TemporalWorkflowStub {
        public static class Builder {
            String group;
            String worker;
            String workflowId;

            public Builder group(String group) {
                this.group = group;
                return this;
            }

            public Builder worker(String worker) {
                this.worker = worker;
                return this;
            }

            public Builder workflowId(String workflowId) {
                this.workflowId = workflowId;
                return this;
            }

            public Literal build() {
                if (this.group == null) {
                    this.group = DEFAULT_WORKFLOW_GROUP_NAME;
                }
                if (this.worker == null) {
                    this.worker = DEFAULT_WORKER;
                }
                if (this.workflowId == null) {
                    this.workflowId = DEFAULT_WORKFLOW_ID;
                }
                return new Literal(group, worker, workflowId);
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        final String group;
        final String worker;
        final String workflowId;

        Literal(String group, String worker, String workflowId) {
            this.group = group;
            this.worker = worker;
            this.workflowId = workflowId;
        }

        @Override
        public String group() {
            return group;
        }

        @Override
        public String worker() {
            return worker;
        }

        @Override
        public String workflowId() {
            return workflowId;
        }
    }
}
