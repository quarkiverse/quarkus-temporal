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

        switch (name) {
            case "quarkus.grpc.clients.temporal-client.host":
                return relocate(context, name, 0);
            case "quarkus.grpc.clients.temporal-client.port":
            case "quarkus.grpc.clients.temporal-client.test-port":
                return relocate(context, name, 1);
        }

        return context.proceed(name);
    }

    private ConfigValue relocate(ConfigSourceInterceptorContext context, String name, int index) {
        ConfigValue existing = context.proceed(name);
        if (existing != null) {
            return existing;
        }
        ConfigValue target = context.proceed("quarkus.temporal.connection.target");
        if (target == null) {
            return null;
        }
        String[] connection = processConnectString(target.getValue());
        return target.from()
                .withName(name)
                .withValue(connection[index])
                .withRawValue(connection[index])
                .build();
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
