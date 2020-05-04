package io.quarkus.grpc.server.devmode;

import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.stub.StreamObserver;

import javax.inject.Singleton;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 4/28/20
 */
@Singleton
public class DevModeTestService extends GreeterGrpc.GreeterImplBase {

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        String greeting = "Hello, ";
        responseObserver.onNext(HelloReply.newBuilder().setMessage(greeting + request.getName()).build());
        responseObserver.onCompleted();
    }
}