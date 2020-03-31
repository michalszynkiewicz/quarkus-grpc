package io.quarkus.grpc.runtime;

import io.grpc.stub.StreamObserver;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClientAndServerCallsTest {

    private FakeServiceClient client = new FakeServiceClient();;

    @Test
    public void oneToOneSuccess() {
        Uni<String> result = ClientCalls.oneToOne("hello", (i, o) -> {
            o.onNext(i);
            o.onCompleted();
        });
        assertThat(result.await().indefinitely()).isEqualTo("hello");
    }

    @Test
    public void oneToOneFailure() {
        Uni<String> result = ClientCalls.oneToOne("hello", (i, o) -> o.onError(new IOException("boom")));
        assertThatThrownBy(() -> result.await().indefinitely()).isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IOException.class)
                .hasMessageContaining("boom");
    }

    @Test
    public void oneToOneFailureAfterEmission() {
        Uni<String> result = ClientCalls.oneToOne("hello", (i, o) -> {
            o.onNext(i);
            o.onError(new IOException("too late"));
        });
        assertThat(result.await().indefinitely()).isEqualTo("hello");
    }

    @Test
    public void testOneToOne() {
        assertThat(client.oneToOne("hello").await().indefinitely()).isEqualTo("HELLO");
    }

    @Test
    public void testOneToMany() {
        assertThat(client.oneToMany("hello").collectItems().asList().await().indefinitely()).containsExactly("HELLO", "HELLO");
    }

    @Test
    public void testManyToOne() {
        assertThat(client.manyToOne(Multi.createFrom().items("hello", "world")).await().indefinitely()).containsExactly("HELLO", "WORLD");
    }

    @Test
    public void testManyToMany() {
        assertThat(client.manyToMany(Multi.createFrom().items("hello", "world"))
                .collectItems().asList()
                .await().indefinitely()).containsExactly("HELLO", "WORLD");
    }

    static class FakeService {

        Uni<String> oneToOne(String s) {
            return Uni.createFrom().item(s).map(String::toUpperCase);
        }

        Uni<List<String>> manyToOne(Multi<String> multi) {
            return multi.map(String::toUpperCase).collectItems().asList();
        }

        Multi<String> oneToMany(String s) {
            return Multi.createFrom().items(s, s).map(String::toUpperCase);
        }

        Multi<String> manyToMany(Multi<String> multi) {
            return multi.map(String::toUpperCase);
        }

    }

    static class FakeServiceClient {

        FakeService service = new FakeService();

        Uni<String> oneToOne(String s) {
            return ClientCalls.oneToOne(s, (i, o) -> ServerCalls.oneToOne(i, o, service::oneToOne));
        }

        Uni<List<String>> manyToOne(Multi<String> multi) {
            return ClientCalls.manyToOne(multi, o -> ServerCalls.manyToOne(o, service::manyToOne));
        }

        Multi<String> oneToMany(String s) {
            return ClientCalls.oneToMany(s, (i, o) -> ServerCalls.oneToMany(i, o, service::oneToMany));
        }

        Multi<String> manyToMany(Multi<String> multi) {
            return ClientCalls.manyToMany(multi, o -> ServerCalls.manyToMany(o, service::manyToMany));
        }

    }

}
