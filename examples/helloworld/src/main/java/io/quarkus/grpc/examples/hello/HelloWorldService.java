package io.quarkus.grpc.examples.hello;

import examples.HelloReply;
import examples.HelloRequest;
import examples.QuarkusGreeterGrpc;
import io.smallrye.mutiny.Uni;

import javax.inject.Singleton;

@Singleton
public class HelloWorldService extends QuarkusGreeterGrpc.GreeterImplBase {

    @Override
    public Uni<HelloReply> sayHello(HelloRequest request) {
        String name = request.getName();
        return Uni.createFrom().item("Hello " + name)
                .map(res -> HelloReply.newBuilder().setMessage(res).build());
    }
}
