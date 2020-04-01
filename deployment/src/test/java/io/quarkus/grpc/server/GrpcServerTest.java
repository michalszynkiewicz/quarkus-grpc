package io.quarkus.grpc.server;

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.quarkus.grpc.runtime.GrpcServerBean;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

public class GrpcServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().setArchiveProducer(
            () -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyFakeService.class, MySecondFakeService.class))
            .withConfigurationResource("application.properties");

    @Inject GrpcServerBean bean;

    @Test
    public void test() {
        assertThat(bean.getServices()).hasSize(2)
                .anySatisfy(b -> assertThat(b.bindService().getServiceDescriptor().getName()).isEqualTo("service1"))
                .anySatisfy(b -> assertThat(b.bindService().getServiceDescriptor().getName()).isEqualTo("service2"));
        assertThat(bean.getGrpcServer().getPort()).isEqualTo(9000);
    }

    @ApplicationScoped
    static class MyFakeService implements BindableService {

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder("service1").build();
        }
    }

    @Dependent
    static class MySecondFakeService implements BindableService {

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder("service2").build();
        }
    }

}
