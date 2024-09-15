package io.quarkiverse.temporal;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;

public class TemporalConfigRelocateInterceptor implements ConfigSourceInterceptor {

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {

        if (name.equals("quarkus.grpc.clients.temporal-client.host")) {

            ConfigValue host = context.proceed("quarkus.grpc.clients.temporal-client.host");
            ConfigValue target = context.proceed("quarkus.temporal.connection.target");
            if (host == null && target != null) {
                String[] split = target.getValue().split(":");
                return target.from()
                        .withName("quarkus.grpc.clients.temporal-client.host")
                        .withValue(split[0])
                        .build();
            }
            return host;
        }

        if (name.equals("quarkus.grpc.clients.temporal-client.port")) {

            ConfigValue port = context.proceed("quarkus.grpc.clients.temporal-client.port");
            ConfigValue target = context.proceed("quarkus.temporal.connection.target");
            if (port == null && target != null) {
                String[] split = target.getValue().split(":");
                return target.from()
                        .withName("quarkus.grpc.clients.temporal-client.port")
                        .withValue(split[1])
                        .build();
            }
            return port;
        }

        if (name.equals("quarkus.grpc.clients.temporal-client.test-port")) {

            ConfigValue port = context.proceed("quarkus.grpc.clients.temporal-client.test-port");
            ConfigValue target = context.proceed("quarkus.temporal.connection.target");
            if (port == null && target != null) {
                String[] split = target.getValue().split(":");
                return target.from()
                        .withName("quarkus.grpc.clients.temporal-client.test-port")
                        .withValue(split[1])
                        .build();
            }
            return port;
        }

        return context.proceed(name);
    }

    @Override
    public Iterator<String> iterateNames(ConfigSourceInterceptorContext context) {
        Set<String> names = new HashSet<>();
        Iterator<String> iterator = context.iterateNames();
        while (iterator.hasNext()) {
            names.add(iterator.next());
        }
        names.add("quarkus.grpc.clients.temporal-client.host");
        names.add("quarkus.grpc.clients.temporal-client.port");
        names.add("quarkus.grpc.clients.temporal-client.test-port");
        return names.iterator();
    }

}
