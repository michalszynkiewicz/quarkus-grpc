package io.quarkus.grpc.runtime;

import io.quarkus.arc.Arc;
import io.quarkus.grpc.runtime.config.GrpcServerConfiguration;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class GrpcServerRecorder {

    public void initializeGrpcServer(GrpcServerConfiguration configuration, ShutdownContext shutdown) {
        Arc.container().instance(GrpcServerBean.class).get().init(configuration, shutdown);
    }

}
