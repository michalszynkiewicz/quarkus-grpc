package io.quarkus.grpc.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class GRPCProcessor {

    public static final String GRPC = "grpc";

    @BuildStep
    public FeatureBuildItem registerFeature() {
        return new FeatureBuildItem(GRPC);
    }
}
