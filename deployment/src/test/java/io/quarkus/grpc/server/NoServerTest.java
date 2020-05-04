package io.quarkus.grpc.server;

import io.quarkus.grpc.runtime.GrpcServerHolder;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verify that no server are started / produced if there is no services
 */
public class NoServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config =
            new QuarkusUnitTest()
                    .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    public void test() {
        assertThat(GrpcServerHolder.server).isNull();
    }
}
