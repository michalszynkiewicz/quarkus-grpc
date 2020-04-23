package io.quarkus.grpc.deployment;

import io.grpc.BindableService;
import io.grpc.Channel;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import io.grpc.netty.NettyChannelProvider;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.processor.BeanConfigurator;
import io.quarkus.arc.processor.BeanStream;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.arc.processor.InjectionPointInfo;
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
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.grpc.runtime.GrpcServerBean;
import io.quarkus.grpc.runtime.GrpcServerRecorder;
import io.quarkus.grpc.runtime.annotations.GrpcService;
import io.quarkus.grpc.runtime.config.GrpcServerBuildTimeConfig;
import io.quarkus.grpc.runtime.config.GrpcServerConfiguration;
import io.quarkus.grpc.runtime.health.GrpcHealthEndpoint;
import io.quarkus.grpc.runtime.health.GrpcHealthStorage;
import io.quarkus.kubernetes.spi.KubernetesPortBuildItem;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;
import io.quarkus.vertx.deployment.VertxBuildItem;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import javax.enterprise.inject.spi.DeploymentException;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.quarkus.grpc.deployment.GrpcDotNames.CREATE_CHANNEL_METHOD;
import static io.quarkus.grpc.deployment.GrpcDotNames.RETRIEVE_CHANNEL_METHOD;

public class GrpcProcessor {

    private static final Logger LOGGER = Logger.getLogger(GrpcProcessor.class.getName());

    public static final String GRPC = "grpc";

    @BuildStep
    public FeatureBuildItem registerFeature() {
        return new FeatureBuildItem(GRPC);
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.unremovableOf(GrpcServerBean.class));
        beans.produce(AdditionalBeanBuildItem.unremovableOf(GrpcService.class));
    }

    @BuildStep
    void discoveryBindableService(BuildProducer<BindableServiceBuildItem> bindables,
            BeanRegistrationPhaseBuildItem index) {
        BeanStream infos = index.getContext().beans().withBeanType(BindableService.class);
        infos.stream().forEach(bi -> bindables.produce(new BindableServiceBuildItem(bi.getBeanClass())));
    }

    @BuildStep
    void discoverInjectedGrpcServices(BeanRegistrationPhaseBuildItem phase,
            BuildProducer<GrpcServiceBuildItem> services) {

        Map<String, GrpcServiceBuildItem> items = new HashMap<>();

        for (InjectionPointInfo injectionPoint : phase.getContext()
                .get(BuildExtension.Key.INJECTION_POINTS)) {
            AnnotationInstance instance = injectionPoint.getRequiredQualifier(GrpcDotNames.GRPC_SERVICE);
            if (instance == null) {
                continue;
            }

            String name = instance.value().asString();
            if (name.trim().isEmpty()) {
                throw new DeploymentException(
                        "Invalid @GrpcService `" + injectionPoint.getTargetInfo() + "` - missing configuration key");
            }

            GrpcServiceBuildItem item;
            if (items.containsKey(name)) {
                item = items.get(name);
            } else {
                item = new GrpcServiceBuildItem(name);
                items.put(name, item);
            }

            Type injectionType = injectionPoint.getRequiredType();
            ClassType type;
            if (injectionType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                // Instance<X>
                type = injectionType.asParameterizedType().arguments().get(0).asClassType();
            } else {
                // X directly
                type = injectionType.asClassType();
            }
            if (!type.name().equals(GrpcDotNames.CHANNEL)) {
                if (isMutinyStub(type.name())) {
                    item.setMutinyStubClass(type);
                } else {
                    item.setBlockingStubClass(type);
                }
            }
        }

        items.values().forEach(item -> {
            services.produce(item);
            LOGGER.infof("Detected GrpcService associated with the '%s' configuration prefix", item.name);
        });
    }

    private boolean isMutinyStub(DotName name) {
        return name.local().startsWith("Mutiny") && name.local().endsWith("Stub");
    }

    @BuildStep
    public void generateGrpcServicesProducers(List<GrpcServiceBuildItem> services,
            BeanRegistrationPhaseBuildItem phase,
            BuildProducer<BeanRegistrationPhaseBuildItem.BeanConfiguratorBuildItem> beans) {
        for (GrpcServiceBuildItem svc : services) {
            // We generate 3 producers:
            // 1. the channel
            // 2. the blocking stub - if blocking stub is set
            // 3. the mutiny stub - if mutiny stub is set

            BeanConfigurator<Object> channelProducer = phase.getContext()
                    .configure(DotName.createSimple(Channel.class.getName()))
                    .types(Channel.class)
                    .addQualifier().annotation(GrpcService.class).addValue("value", svc.getServiceName()).done()
                    .scope(Singleton.class)
                    .unremovable()
                    .creator(mc -> generateChannelProducer(mc, svc));
            channelProducer.done();
            beans.produce(new BeanRegistrationPhaseBuildItem.BeanConfiguratorBuildItem(channelProducer));

            if (svc.blockingStubClass != null) {
                BeanConfigurator<Object> blockingStubProducer = phase.getContext()
                        .configure(svc.blockingStubClass.name())
                        .types(svc.blockingStubClass)
                        .addQualifier().annotation(GrpcService.class).addValue("value", svc.getServiceName()).done()
                        .scope(Singleton.class)
                        .creator(mc -> generateStubProducer(mc, svc, false));
                blockingStubProducer.done();
                beans.produce(new BeanRegistrationPhaseBuildItem.BeanConfiguratorBuildItem(blockingStubProducer));
            }

            if (svc.mutinyStubClass != null) {
                BeanConfigurator<Object> blockingStubProducer = phase.getContext()
                        .configure(svc.mutinyStubClass.name())
                        .types(svc.mutinyStubClass)
                        .addQualifier().annotation(GrpcService.class).addValue("value", svc.getServiceName()).done()
                        .scope(Singleton.class)
                        .creator(mc -> generateStubProducer(mc, svc, true));
                blockingStubProducer.done();
                beans.produce(new BeanRegistrationPhaseBuildItem.BeanConfiguratorBuildItem(blockingStubProducer));
            }
        }
    }

    private void generateChannelProducer(MethodCreator mc, GrpcServiceBuildItem svc) {
        ResultHandle prefix = mc.load(svc.getConfigPrefix());
        ResultHandle result = mc.invokeStaticMethod(CREATE_CHANNEL_METHOD, prefix);
        mc.returnValue(result);
        mc.close();
    }

    private void generateStubProducer(MethodCreator mc, GrpcServiceBuildItem svc, boolean mutiny) {
        ResultHandle prefix = mc.load(svc.getServiceName());
        ResultHandle channel = mc.invokeStaticMethod(RETRIEVE_CHANNEL_METHOD, prefix);

        MethodDescriptor descriptor;
        if (mutiny) {
            descriptor = MethodDescriptor
                    .ofMethod(svc.getMutinyGrpcServiceName(), "newMutinyStub", svc.mutinyStubClass.name().toString(),
                            Channel.class.getName());
        } else {
            descriptor = MethodDescriptor
                    .ofMethod(svc.getBlockingGrpcServiceName(), "newBlockingStub",
                            svc.blockingStubClass.name().toString(),
                            Channel.class.getName());
        }

        ResultHandle stub = mc.invokeStaticMethod(descriptor, channel);
        mc.returnValue(stub);
        mc.close();
    }

    @BuildStep(onlyIf = IsNormal.class)
    public KubernetesPortBuildItem registerGrpcServiceInKubernetes(List<BindableServiceBuildItem> bindables) {
        if (!bindables.isEmpty()) {
            int port = ConfigProvider.getConfig().getOptionalValue("quarkus.grpc-server.port", Integer.class)
                    .orElse(9000);
            return new KubernetesPortBuildItem(port, "grpc");
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
            return new ServiceStartBuildItem("grpc-server");
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

        extensionSslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(GRPC));
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
                config.mpHealthEnabled, "grpc-server");
    }
}
