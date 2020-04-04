package io.quarkus.grpc.examples.hello;

import examples.GreeterGrpc;
import examples.HelloReply;
import examples.HelloRequest;
import examples.MutinyGreeterGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class HelloWorldEndpointTest {

    @Test
    public void testHelloWorldServiceUsingBlockingStub() {
        String response = get("/hello/blocking/neo").asString();
        assertThat(response).isEqualTo("Hello neo");
    }

    @Test
    public void testHelloWorldServiceUsingMutinyStub() {
        String response = get("/hello/mutiny/neo-mutiny").asString();
        assertThat(response).isEqualTo("Hello neo-mutiny");
    }


}