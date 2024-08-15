package io.quarkiverse.temporal;

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
    String DEFAULT_WORKFLOW_ID = "io.quarkiverse.temporal.TemporalWorkflowStub.workflowId.DEFAULT";

    String worker();

    @Nonbinding
    String workflowId() default DEFAULT_WORKFLOW_ID;

    class Literal extends AnnotationLiteral<TemporalWorkflowStub> implements TemporalWorkflowStub {
        final String worker;
        final String workflowId;

        public Literal(String worker, String workflowId) {
            this.worker = worker;
            this.workflowId = workflowId;
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
