package io.quarkus.grpc.server;

import com.google.protobuf.ByteString;
import com.google.protobuf.EmptyProtos;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.QuarkusGreeterGrpc;
import io.grpc.testing.integration.Messages;
import io.grpc.testing.integration.QuarkusTestServiceGrpc;
import io.grpc.testing.integration.TestServiceGrpc;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GrpcServiceTestBase {

    protected ManagedChannel channel;

    @BeforeEach
    public void init() {
        channel = ManagedChannelBuilder.forAddress("localhost", 9000)
                .usePlaintext()
                .build();
    }

    @AfterEach
    public void shutdown() {
        channel.shutdownNow();
    }

    @Test
    public void testHelloWithBlockingClient() {
        HelloReply reply = GreeterGrpc.newBlockingStub(channel)
                .sayHello(HelloRequest.newBuilder().setName("neo").build());
        assertThat(reply.getMessage()).isEqualTo("Hello neo");
    }

    @Test
    public void testHelloWithMutinyClient() {
        Uni<HelloReply> reply = QuarkusGreeterGrpc.newQuarkusStub(channel)
                .sayHello(HelloRequest.newBuilder().setName("neo").build());
        assertThat(reply.await().indefinitely().getMessage()).isEqualTo("Hello neo");
    }

    @Test
    public void testEmptyWithBlockingClient() {
        EmptyProtos.Empty empty = TestServiceGrpc.newBlockingStub(channel)
                .emptyCall(EmptyProtos.Empty.newBuilder().build());
        assertThat(empty).isNotNull();
    }

    @Test
    public void testEmptyWithMutinyClient() {
        EmptyProtos.Empty empty = QuarkusTestServiceGrpc.newQuarkusStub(channel)
                .emptyCall(EmptyProtos.Empty.newBuilder().build()).await().indefinitely();
        assertThat(empty).isNotNull();
    }

    @Test
    public void testUnaryMethodWithBlockingClient() {
        Messages.SimpleResponse response = TestServiceGrpc.newBlockingStub(channel)
                .unaryCall(Messages.SimpleRequest.newBuilder().build());
        assertThat(response).isNotNull();
    }

    @Test
    public void testUnaryMethodWithMutinyClient() {
        Messages.SimpleResponse response = QuarkusTestServiceGrpc.newQuarkusStub(channel)
                .unaryCall(Messages.SimpleRequest.newBuilder().build()).await().indefinitely();
        assertThat(response).isNotNull();
    }

    @Test
    public void testStreamingOutMethodWithBlockingClient() {
        Iterator<Messages.StreamingOutputCallResponse> iterator = TestServiceGrpc
                .newBlockingStub(channel)
                .streamingOutputCall(Messages.StreamingOutputCallRequest.newBuilder().build());
        assertThat(iterator).isNotNull();
        List<String> list = new CopyOnWriteArrayList<>();
        iterator.forEachRemaining(so -> {
            String content = so.getPayload().getBody().toStringUtf8();
            list.add(content);
        });
        assertThat(list).containsExactly("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
    }

    @Test
    public void testStreamingOutMethodWithMutinyClient() {
        Multi<Messages.StreamingOutputCallResponse> multi = QuarkusTestServiceGrpc
                .newQuarkusStub(channel)
                .streamingOutputCall(Messages.StreamingOutputCallRequest.newBuilder().build());
        assertThat(multi).isNotNull();
        List<String> list = multi.map(o -> o.getPayload().getBody().toStringUtf8()).collectItems().asList()
                .await().indefinitely();
        assertThat(list).containsExactly("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
    }

    @Test
    public void testStreamingInMethodWithMutinyClient() {
        Multi<Messages.StreamingInputCallRequest> input = Multi.createFrom().items("a", "b", "c", "d")
                .map(s -> Messages.Payload.newBuilder().setBody(ByteString.copyFromUtf8(s)).build())
                .map(p -> Messages.StreamingInputCallRequest.newBuilder().setPayload(p).build());
        Uni<Messages.StreamingInputCallResponse> done  = QuarkusTestServiceGrpc
                .newQuarkusStub(channel).streamingInputCall(input);
        assertThat(done).isNotNull();
        done.await().indefinitely();
    }

    @Test
    public void testFullDuplexMethodWithMutinyClient() {
        Multi<Messages.StreamingOutputCallRequest> input = Multi.createFrom().items("a", "b", "c", "d")
                .map(s -> Messages.Payload.newBuilder().setBody(ByteString.copyFromUtf8(s)).build())
                .map(p -> Messages.StreamingOutputCallRequest.newBuilder().setPayload(p).build());
        List<String> response  = QuarkusTestServiceGrpc
                .newQuarkusStub(channel).fullDuplexCall(input)
                .map(o -> o.getPayload().getBody().toStringUtf8())
                .collectItems().asList()
                .await().indefinitely();
        assertThat(response).isNotNull();
        assertThat(response).containsExactly("a1", "b2", "c3", "d4");
    }

    @Test
    public void testHalfDuplexMethodWithMutinyClient() {
        Multi<Messages.StreamingOutputCallRequest> input = Multi.createFrom().items("a", "b", "c", "d")
                .map(s -> Messages.Payload.newBuilder().setBody(ByteString.copyFromUtf8(s)).build())
                .map(p -> Messages.StreamingOutputCallRequest.newBuilder().setPayload(p).build());
        List<String> response  = QuarkusTestServiceGrpc
                .newQuarkusStub(channel).halfDuplexCall(input)
                .map(o -> o.getPayload().getBody().toStringUtf8())
                .collectItems().asList()
                .await().indefinitely();
        assertThat(response).isNotNull();
        assertThat(response).containsExactly("A", "B", "C", "D");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testUnimplementedMethodWithBlockingClient() {
        assertThatThrownBy(() ->
                TestServiceGrpc.newBlockingStub(channel).unimplementedCall(EmptyProtos.Empty.newBuilder().build())
        ).isInstanceOf(StatusRuntimeException.class).hasMessageContaining("UNIMPLEMENTED");
    }

    @Test
    public void testUnimplementedMethodWithMutinyClient() {
        assertThatThrownBy(() ->
                QuarkusTestServiceGrpc.newQuarkusStub(channel).unimplementedCall(EmptyProtos.Empty.newBuilder().build())
                        .await().indefinitely()
        ).isInstanceOf(StatusRuntimeException.class).hasMessageContaining("UNIMPLEMENTED");
    }
}
