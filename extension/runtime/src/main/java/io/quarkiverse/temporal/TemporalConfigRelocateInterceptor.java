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

        ConfigValue target = context.proceed("quarkus.temporal.connection.target");
        if (target == null) {
            return context.proceed(name);
        }
        String[] connection = processConnectString(target.getValue());

        switch (name) {
            case "quarkus.grpc.clients.temporal-client.host": {

                ConfigValue host = context.proceed("quarkus.grpc.clients.temporal-client.host");
                if (host == null && connection[0] != null) {
                    return target.from()
                            .withName("quarkus.grpc.clients.temporal-client.host")
                            .withValue(connection[0])
                            .withRawValue(connection[0])
                            .build();
                }
                return host;
            }
            case "quarkus.grpc.clients.temporal-client.port": {

                ConfigValue port = context.proceed("quarkus.grpc.clients.temporal-client.port");
                if (port == null && connection[1] != null) {
                    return target.from()
                            .withName("quarkus.grpc.clients.temporal-client.port")
                            .withValue(connection[1])
                            .withRawValue(connection[1])
                            .build();
                }
                return port;
            }
            case "quarkus.grpc.clients.temporal-client.test-port": {

                ConfigValue port = context.proceed("quarkus.grpc.clients.temporal-client.test-port");
                if (port == null && connection[1] != null) {
                    return target.from()
                            .withName("quarkus.grpc.clients.temporal-client.test-port")
                            .withValue(connection[1])
                            .withRawValue(connection[1])
                            .build();
                }
                return port;
            }
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

    /**
     * Processes a connection string and extracts the host and port.
     * The input can either be in the format "${PLACEHOLDER:host:port}" or "host:port".
     * If the input contains a placeholder in the form of "${...}", the method removes it
     * and returns the host and port. If the port is not provided, the port will be an empty string.
     *
     * @param input the connection string which may include a placeholder in the form "${...}" or just "host:port".
     * @return a String array where the first element is the host and the second element is the port. If the port is
     *         not provided, the second element will be an empty string.
     * @throws IllegalArgumentException if the input is null or empty.
     */
    private static String[] processConnectString(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("'quarkus.temporal.connection.target' cannot be null or empty");
        }

        // Check if the string starts with "${" and contains a "}"
        input = input.replaceAll("^\\$\\{", "");
        input = input.replaceAll("}", "");

        // Split by colon
        String[] parts = input.split(":");

        // Validate that there are at least two parts (host and port)
        if (parts.length >= 2) {
            String host = parts[parts.length - 2];
            String port = parts[parts.length - 1];
            // Return the host and port as a string array
            return new String[] { host, port };
        } else {
            throw new IllegalArgumentException("'quarkus.temporal.connection.target' is not in host:port format.");
        }
    }
}