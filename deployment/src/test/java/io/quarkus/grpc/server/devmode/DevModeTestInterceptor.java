package io.quarkus.grpc.server.devmode;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DevModeTestInterceptor implements ServerInterceptor {

    private volatile String lastStatus = "initial";
    private volatile long lastCallTime;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall,
                                                                 Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        return serverCallHandler
                .startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(serverCall) {
                    @Override
                    protected ServerCall<ReqT, RespT> delegate() {
                        lastStatus = getStatus();
                        lastCallTime = System.currentTimeMillis(); // mstodo remove or replace lastStatus with it
                        return super.delegate();
                    }
                }, metadata);
    }

    public String getLastStatus() {
        return lastStatus;
    }

    private String getStatus() {
        return "status";
    }
}
