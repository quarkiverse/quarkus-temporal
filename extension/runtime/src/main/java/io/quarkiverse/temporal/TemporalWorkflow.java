package io.quarkiverse.temporal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface TemporalWorkflow {

    /**
     * @return names of Workers to register this activity bean with. Workers with these names must be
     *         present in the application config. Worker is named by its task queue if its name is not
     *         specified.
     */
    String[] workers() default {};

}
