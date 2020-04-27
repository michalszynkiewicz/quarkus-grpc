package io.quarkus.grpc.runtime;

import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.internal.ServerImpl;
import io.vertx.grpc.VertxServer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

public class GrpcServerHolder {

    public static volatile VertxServer server;

    public static void reset() {
        try {
            Field registryField = ServerImpl.class.getDeclaredField("registry");
            registryField.setAccessible(true);

            Object registryObject = registryField.get(server.getRawServer());
            forceSet(registryObject, "services", null);
            forceSet(registryObject, "methods", null);
            forceSet(server.getRawServer(), "interceptors", null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to reinitialize gRPC server", e);
        }
    }

    public static void reinitialize(List<ServerServiceDefinition> serviceDefinitions,
                                    Map<String, ServerMethodDefinition<?, ?>> methods,
                                    List<ServerInterceptor> sortedInterceptors) {
        try {
            Field registryField = ServerImpl.class.getDeclaredField("registry");
            registryField.setAccessible(true);

            Object registryObject = registryField.get(server.getRawServer());
            forceSet(registryObject, "services", serviceDefinitions);
            forceSet(registryObject, "methods", methods);

            ServerInterceptor[] interceptorsArray =
                    sortedInterceptors.toArray(new ServerInterceptor[sortedInterceptors.size()]);
            forceSet(server.getRawServer(), "interceptors", interceptorsArray);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to nullify gRPC server data", e);
        }
    }

    private static void forceSet(Object object, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        unfinal(field);

        field.set(object, value);
    }

    /**
     * make the field non-final
     * @param field field to alter
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private static void unfinal(Field field) throws NoSuchFieldException, IllegalAccessException {
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    }
}
