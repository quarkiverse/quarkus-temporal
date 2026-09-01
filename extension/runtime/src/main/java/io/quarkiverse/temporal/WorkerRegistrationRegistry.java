package io.quarkiverse.temporal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Captures worker/workflow-type registrations performed at boot so they can be replayed
 * against a freshly created {@link io.temporal.worker.WorkerFactory} in test/mock mode.
 *
 * Populated unconditionally by {@link WorkerFactoryRecorder#createWorker}; only ever read
 * by the {@code quarkus-temporal-test} extension.
 */
public final class WorkerRegistrationRegistry {

    private static final List<Runnable> REGISTRATIONS = new CopyOnWriteArrayList<>();

    private WorkerRegistrationRegistry() {
    }

    public static void record(Runnable registration) {
        REGISTRATIONS.add(registration);
    }

    public static void replayAll() {
        for (Runnable registration : REGISTRATIONS) {
            registration.run();
        }
    }
}
