package io.quarkus.grpc.server;

import com.google.protobuf.EmptyProtos;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.examples.helloworld.*;
import io.grpc.testing.integration.Messages;
import io.grpc.testing.integration.QuarkusTestServiceGrpc;
import io.grpc.testing.integration.TestServiceGrpc;
import io.quarkus.grpc.server.services.HelloService;
import io.quarkus.grpc.server.services.MutinyHelloService;
import io.quarkus.grpc.server.services.MutinyTestService;
import io.quarkus.grpc.server.services.TestService;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test services exposed by the gRPC server implemented using the Mutiny gRPC model.
 * Communication use plain-text.
 */
public class MutinyGrpcServiceWithPlainTextTest extends GrpcServiceTestBase {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().setArchiveProducer(
            () -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MutinyHelloService.class, MutinyTestService.class,
                            GreeterGrpc.class, HelloRequest.class, HelloReply.class, QuarkusGreeterGrpc.class,
                            HelloRequestOrBuilder.class, HelloReplyOrBuilder.class,
                            EmptyProtos.class, Messages.class, QuarkusTestServiceGrpc.class,
                            TestServiceGrpc.class)
    );


}
