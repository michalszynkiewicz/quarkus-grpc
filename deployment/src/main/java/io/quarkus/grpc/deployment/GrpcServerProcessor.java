package io.quarkus.grpc.deployment;

import io.grpc.BindableService;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import io.grpc.netty.NettyChannelProvider;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.processor.BeanStream;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.grpc.runtime.GrpcServerBean;
import io.quarkus.grpc.runtime.GrpcServerRecorder;
import io.quarkus.grpc.runtime.config.GrpcServerBuildTimeConfig;
import io.quarkus.grpc.runtime.config.GrpcServerConfiguration;
import io.quarkus.grpc.runtime.health.GrpcHealthEndpoint;
import io.quarkus.grpc.runtime.health.GrpcHealthStorage;
import io.quarkus.kubernetes.spi.KubernetesPortBuildItem;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;
import io.quarkus.vertx.deployment.VertxBuildItem;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.ClassInfo;

import java.util.Collection;
import java.util.List;

public class GrpcServerProcessor {

    public static final String GRPC_SERVER = "grpc-server";

    @BuildStep
    public FeatureBuildItem registerFeature() {
        return new FeatureBuildItem(GRPC_SERVER);
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.unremovableOf(GrpcServerBean.class));
    }

    @BuildStep
    void discoverBindableServices(BuildProducer<BindableServiceBuildItem> bindables,
            BeanRegistrationPhaseBuildItem index) {
        BeanStream infos = index.getContext().beans().withBeanType(BindableService.class);
        infos.stream().forEach(bi -> bindables.produce(new BindableServiceBuildItem(bi.getBeanClass())));
    }

    @BuildStep(onlyIf = IsNormal.class)
    public KubernetesPortBuildItem registerGrpcServiceInKubernetes(List<BindableServiceBuildItem> bindables) {
        if (!bindables.isEmpty()) {
            int port = ConfigProvider.getConfig().getOptionalValue("quarkus.grpc-server.port", Integer.class)
                    .orElse(9000);
            return new KubernetesPortBuildItem(port, GRPC_SERVER);
        }
        return null;
    }

    @BuildStep
    @Record(value = ExecutionTime.RUNTIME_INIT)
    ServiceStartBuildItem build(GrpcServerRecorder recorder, GrpcServerConfiguration config,
            ShutdownContextBuildItem shutdown, List<BindableServiceBuildItem> bindables,
            VertxBuildItem vertx) {
        if (!bindables.isEmpty()) {
            recorder.initializeGrpcServer(config, shutdown);
            return new ServiceStartBuildItem(GRPC_SERVER);
        }
        return null;
    }

    @BuildStep
    public void configureNativeExecutable(CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<ExtensionSslNativeSupportBuildItem> extensionSslNativeSupport) {

        // we force the usage of the reflection invoker.
        Collection<ClassInfo> messages = combinedIndex.getIndex()
                .getAllKnownSubclasses(GrpcDotNames.GENERATED_MESSAGE_V3);
        for (ClassInfo message : messages) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, true, message.name().toString()));
        }
        Collection<ClassInfo> builders = combinedIndex.getIndex().getAllKnownSubclasses(GrpcDotNames.MESSAGE_BUILDER);
        for (ClassInfo builder : builders) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, true, builder.name().toString()));
        }

        Collection<ClassInfo> lbs = combinedIndex.getIndex().getAllKnownSubclasses(GrpcDotNames.LOAD_BALANCER_PROVIDER);
        for (ClassInfo lb : lbs) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, false, lb.name().toString()));
        }

        Collection<ClassInfo> nrs = combinedIndex.getIndex().getAllKnownSubclasses(GrpcDotNames.NAME_RESOLVER_PROVIDER);
        for (ClassInfo nr : nrs) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, false, nr.name().toString()));
        }

        // Built-In providers:
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, false, DnsNameResolverProvider.class));
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, false, PickFirstLoadBalancerProvider.class));
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, false,
                "io.grpc.util.SecretRoundRobinLoadBalancerProvider$Provider"));
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, false, NettyChannelProvider.class));

        extensionSslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(GRPC_SERVER));
    }

    @BuildStep
    HealthBuildItem addHealthChecks(GrpcServerBuildTimeConfig config,
            BuildProducer<AdditionalBeanBuildItem> beans) {
        boolean healthEnabled = config.mpHealthEnabled;
        if (config.grpcHealthEnabled) {
            beans.produce(AdditionalBeanBuildItem.unremovableOf(GrpcHealthEndpoint.class));
            healthEnabled = true;
        }

        if (healthEnabled) {
            beans.produce(AdditionalBeanBuildItem.unremovableOf(GrpcHealthStorage.class));
        }
        return new HealthBuildItem("io.quarkus.grpc.runtime.health.GrpcHealthCheck",
                config.mpHealthEnabled, GRPC_SERVER);
    }
}
