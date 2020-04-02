package io.quarkus.grpc.server.interceptors;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloReplyOrBuilder;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.HelloRequestOrBuilder;
import io.grpc.examples.helloworld.QuarkusGreeterGrpc;
import io.quarkus.grpc.server.services.MutinyHelloService;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerInterceptorPriorityTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().setArchiveProducer(
            () -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MutinyHelloService.class, MyFirstInterceptor.class, MySecondInterceptor.class,
                            GreeterGrpc.class, HelloRequest.class, HelloReply.class, QuarkusGreeterGrpc.class,
                            HelloRequestOrBuilder.class, HelloReplyOrBuilder.class));

    protected ManagedChannel channel;

    @Inject MyFirstInterceptor interceptor1;
    @Inject MySecondInterceptor interceptor2;

    @BeforeEach
    public void init() {
        channel = ManagedChannelBuilder.forAddress("localhost", 9000)
                .usePlaintext()
                .build();
    }

    @AfterEach
    public void shutdown() {
        if (channel != null) {
            channel.shutdownNow();
        }
    }

    @Test
    public void testInterceptors() {
        HelloReply reply = GreeterGrpc.newBlockingStub(channel)
                .sayHello(HelloRequest.newBuilder().setName("neo").build());
        assertThat(reply.getMessage()).isEqualTo("Hello neo");

        assertThat(interceptor1.getLastCall()).isNotZero();
        assertThat(interceptor2.getLastCall()).isNotZero();

        assertThat(interceptor2.getLastCall()).isGreaterThan(interceptor1.getLastCall());
    }


    // Test ordering with 1 without priority
}
