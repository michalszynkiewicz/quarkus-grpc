package io.quarkus.grpc.examples.hello;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class HelloWorldMutualTlsEndpointTest {

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