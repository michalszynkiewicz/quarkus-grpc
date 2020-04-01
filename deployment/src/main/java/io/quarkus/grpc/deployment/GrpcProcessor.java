package io.quarkus.grpc.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.grpc.runtime.GrpcServerBean;
import io.quarkus.grpc.runtime.GrpcServerConfiguration;
import io.quarkus.grpc.runtime.GrpcServerRecorder;
import io.quarkus.vertx.deployment.VertxBuildItem;

public class GrpcProcessor {

    public static final String GRPC = "grpc";

    @BuildStep
    public FeatureBuildItem registerFeature() {
        return new FeatureBuildItem(GRPC);
    }

    @BuildStep
    AdditionalBeanBuildItem registerBeans() {
        return AdditionalBeanBuildItem.unremovableOf(GrpcServerBean.class);
    }

    @BuildStep
    @Record(value = ExecutionTime.RUNTIME_INIT)
    ServiceStartBuildItem build(GrpcServerRecorder recorder, GrpcServerConfiguration config, VertxBuildItem vertx) {
        recorder.initializeGrpcServer(config);
        return new ServiceStartBuildItem("grpc-server");
    }

}
