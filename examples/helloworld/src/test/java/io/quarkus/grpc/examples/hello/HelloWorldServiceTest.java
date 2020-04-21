package io.quarkus.grpc.examples.hello;

import examples.GreeterGrpc;
import examples.HelloReply;
import examples.HelloRequest;
import examples.MutinyGreeterGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class HelloWorldServiceTest {

    private ManagedChannel channel;

    @BeforeEach
    public void init() {
        channel = ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext().build();
    }

    @AfterEach
    public void cleanup() throws InterruptedException {
        channel.shutdown();
        channel.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    public void testHelloWorldServiceUsingBlockingStub() {
        GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel);
        HelloReply reply = client
                .sayHello(HelloRequest.newBuilder().setName("neo-blocking").build());
        assertThat(reply.getMessage()).isEqualTo("Hello neo-blocking");
    }

    @Test
    public void testHelloWorldServiceUsingMutinyStub() {
        HelloReply reply = MutinyGreeterGrpc.newMutinyStub(channel)
                .sayHello(HelloRequest.newBuilder().setName("neo-blocking").build())
                .await().atMost(Duration.ofSeconds(5));
        assertThat(reply.getMessage()).isEqualTo("Hello neo-blocking");
    }

}