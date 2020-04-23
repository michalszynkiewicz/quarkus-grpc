package io.quarkus.grpc.server;

import io.grpc.BindableService;
import io.quarkus.grpc.runtime.GrpcServerBean;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.grpc.VertxServer;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verify that no server are started / produced if there is no services
 */
public class NoServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config =
            new QuarkusUnitTest()
                    .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject GrpcServerBean bean;

    @Test
    public void test() {
        assertThat(bean).isNotNull();
        VertxServer server = bean.getGrpcServer();
        assertThat(server).isNull();
    }
}
