package io.quarkus.grpc.server.devmode;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloReplyOrBuilder;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.HelloRequestOrBuilder;
import io.quarkus.grpc.runtime.GrpcServerBean;
import io.quarkus.grpc.server.services.HelloService;
import io.quarkus.test.QuarkusDevModeTest;
import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 4/28/20
 */
public class DevModeTest {
    @RegisterExtension
    public static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(DevModeTestService.class, DevModeTestInterceptor.class, DevModeTestRestResource.class)
            .addPackage(GreeterGrpc.class.getPackage())
            .addPackage(HelloReply.class.getPackage()));

    protected ManagedChannel channel;

    @BeforeEach
    public void init() {
        channel = ManagedChannelBuilder.forAddress("localhost", 9000)
                .usePlaintext()
                .build();
    }


    @Test
    public void testInterceptorReload() {
        callHello("Pooh", ".*Pooh");

        assertThat(when().get("/test/interceptor-status").asString()).isEqualTo("status");

        test.modifySourceFile("DevModeTestInterceptor.java",
                text -> {
                    String replaced = text.replace("return \"status\"", "return \"altered-status\"");
                    System.out.println(replaced);
                    return replaced; // mstodo remove println
                }
        );

        callHello("Pooh", ".*Pooh");
        assertThat(when().get("/test/interceptor-status").asString()).isEqualTo("altered-status");
    }

    @Test
    public void testSingleReload() throws InterruptedException {
        callHello("Pooh", "Hello, Pooh");
        test.modifySourceFile("DevModeTestService.java",
                text -> text.replaceAll("String greeting = .*;", "String greeting = \"hello, \";")
        );
        channel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
        channel = ManagedChannelBuilder.forAddress("localhost", 9000)
                .usePlaintext()
                .build();

        callHello("Pooh", "hello, Pooh");
    }

    @Test
    public void testStreamReload() {

    }

    @Test
    public void testReloadAfterRest() {

    }

    @Test
    public void testReloadBeforeRest() {

    }

    // TODO: implement when we have class from proto generation as a Quarkus build step
    public void testProtoFileChangeReload() {
        fail("Not implemented.");
    }

    private void callHello(String name, String responseMatcher) {
        HelloReply reply = GreeterGrpc.newBlockingStub(channel)
                .sayHello(HelloRequest.newBuilder().setName(name).build());
        assertThat(reply.getMessage()).matches(responseMatcher);
    }
}
