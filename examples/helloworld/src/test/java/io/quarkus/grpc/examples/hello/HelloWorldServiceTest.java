package io.quarkus.grpc.examples.hello;

import examples.GreeterGrpc;
import examples.HelloReply;
import examples.HelloRequest;
import examples.QuarkusGreeterGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class HelloWorldServiceTest {

    @Test
    public void testHelloWorldServiceUsingBlockingStub() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext().build();
        HelloReply reply = GreeterGrpc.newBlockingStub(channel)
                .sayHello(HelloRequest.newBuilder().setName("neo-blocking").build());
        assertThat(reply.getMessage()).isEqualTo("Hello neo-blocking");
    }

    @Test
    public void testHelloWorldServiceUsingMutinyStub() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext().build();
        HelloReply reply = QuarkusGreeterGrpc.newQuarkusStub(channel)
                .sayHello(HelloRequest.newBuilder().setName("neo-blocking").build()).await().indefinitely();
        assertThat(reply.getMessage()).isEqualTo("Hello neo-blocking");
    }

}