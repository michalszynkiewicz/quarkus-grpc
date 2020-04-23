package io.quarkus.grpc.deployment;

import io.quarkus.builder.item.MultiBuildItem;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

public final class BindableServiceBuildItem extends MultiBuildItem {

    final DotName serviceClass;

    public BindableServiceBuildItem(DotName serviceClass) {
        this.serviceClass = serviceClass;
    }

}
