package io.quarkus.grpc.server.devmode;

import com.example.test.MutinyStreamsGrpc;
import com.example.test.StreamsGrpc;
import com.example.test.StreamsOuterClass;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.quarkus.test.QuarkusDevModeTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Subscribers;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

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
                            .addClasses(DevModeTestService.class, DevModeTestStreamService.class, DevModeTestInterceptor.class, DevModeTestRestResource.class)
                            .addPackage(GreeterGrpc.class.getPackage()).addPackage(HelloReply.class.getPackage())
                            .addPackage(StreamsGrpc.class.getPackage()).addPackage(StreamsOuterClass.Item.class.getPackage()));

    protected ManagedChannel channel;

    @BeforeEach
    public void init() {
        channel = ManagedChannelBuilder.forAddress("localhost", 9000)
                .usePlaintext()
                .build();
    }

    @AfterEach
    public void shutdown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    public void testInterceptorReload() {
        callHello("Pooh", ".*Pooh");

        assertThat(when().get("/test/interceptor-status").asString()).isEqualTo("status");

        test.modifySourceFile("DevModeTestInterceptor.java",
                text -> text.replace("return \"status\"", "return \"altered-status\"")
        );

        callHello("Pooh", ".*Pooh");
        assertThat(when().get("/test/interceptor-status").asString()).isEqualTo("altered-status");
    }

    @Test
    public void testSingleReload() {
        callHello("Pooh", "Hello, Pooh");
        test.modifySourceFile("DevModeTestService.java",
                text -> text.replaceAll("String greeting = .*;", "String greeting = \"hello, \";")
        );
        callHello("Pooh", "hello, Pooh");
    }

    @Test
    public void testReloadAfterRest() {
        test.modifySourceFile("DevModeTestService.java",
                text -> text.replaceAll("String greeting = .*;", "String greeting = \"hell no, \";")
        );
        test.modifySourceFile("DevModeTestRestResource.java",
                text -> text.replace("testresponse", "testresponse2")
        );

        assertThat(when().get("/test").asString()).isEqualTo("testresponse2");
        callHello("Pooh", "hell no, Pooh");
    }

    @Test
    public void testReloadBeforeRest() {
        test.modifySourceFile("DevModeTestService.java",
                text -> text.replaceAll("String greeting = .*;", "String greeting = \"hell yes, \";")
        );
        test.modifySourceFile("DevModeTestRestResource.java",
                text -> text.replace("testresponse", "testresponse3")
        );

        callHello("Pooh", "hell yes, Pooh");
        assertThat(when().get("/test").asString()).isEqualTo("testresponse3");
    }

    @Test
    public void testEchoStreamReload() {
        final CopyOnWriteArrayList<String> results = new CopyOnWriteArrayList<>();
        CompletionStage<Boolean> firstStreamFinished = callEcho("foo", results);
        Awaitility.await().atMost(1, TimeUnit.SECONDS)
                .until(() -> results, Matchers.hasItem("echo::foo"));

        test.modifySourceFile("DevModeTestStreamService.java",
                text -> text.replace("echo::", "newecho::")
        );

        final CopyOnWriteArrayList<String> newResults = new CopyOnWriteArrayList<>();
        callEcho("foo", newResults);
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> newResults, Matchers.hasItem("newecho::foo"));
        assertThat(firstStreamFinished).isCompleted();
    }


    // TODO: implement when we have class from proto generation as a Quarkus build step
    public void testProtoFileChangeReload() {
        fail("Not implemented.");
    }

    private CompletionStage<Boolean> callEcho(String name, List<String> output) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Multi<StreamsOuterClass.Item> request = Multi.createFrom()
                .item(name)
                .map(StreamsOuterClass.Item.newBuilder()::setName)
                .map(StreamsOuterClass.Item.Builder::build);
        Multi<StreamsOuterClass.Item> echo = MutinyStreamsGrpc.newMutinyStub(channel)
                .echo(request);
        echo.subscribe().withSubscriber(Subscribers.<StreamsOuterClass.Item>from(
                item -> output.add(item.getName()),
                error -> {
                    error.printStackTrace();
                    result.completeExceptionally(error);
                },
                () -> result.complete(true),
                s -> s.request(Long.MAX_VALUE)));
        return result;
    }
    private void callHello(String name, String responseMatcher) {
        HelloReply reply = GreeterGrpc.newBlockingStub(channel)
                .sayHello(HelloRequest.newBuilder().setName(name).build());
        assertThat(reply.getMessage()).matches(responseMatcher);
    }
}
