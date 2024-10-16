package io.quarkiverse.temporal.it.cdi;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CdiBean {

    public void someMethod() {
        System.out.println("Calling CDI Bean from Activity");
        System.out.flush();
    }
}
