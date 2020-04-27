package io.quarkus.grpc.server;

import io.quarkus.grpc.runtime.GrpcServerBean;
import io.quarkus.grpc.runtime.GrpcServerHolder;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.inject.Inject;

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
        assertThat(GrpcServerHolder.server).isNull();
    }
}
