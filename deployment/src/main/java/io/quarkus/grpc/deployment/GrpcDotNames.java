package io.quarkus.grpc.deployment;

import io.grpc.Channel;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.grpc.runtime.annotations.GrpcService;
import io.quarkus.grpc.runtime.supports.Channels;
import org.jboss.jandex.DotName;

public class GrpcDotNames {

    static final DotName CHANNEL = DotName.createSimple(Channel.class.getName());
    static final DotName GRPC_SERVICE = DotName.createSimple(GrpcService.class.getName());


    static final MethodDescriptor CREATE_CHANNEL_METHOD = MethodDescriptor.ofMethod(Channels.class, "createChannel", Channel.class, String.class);
    static final MethodDescriptor RETRIEVE_CHANNEL_METHOD = MethodDescriptor.ofMethod(Channels.class, "retrieveChannel", Channel.class, String.class);

}
