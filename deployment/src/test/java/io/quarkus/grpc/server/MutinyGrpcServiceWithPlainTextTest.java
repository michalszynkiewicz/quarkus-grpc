package io.quarkus.grpc.server;

import com.google.protobuf.EmptyProtos;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloReplyOrBuilder;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.HelloRequestOrBuilder;
import io.grpc.examples.helloworld.QuarkusGreeterGrpc;
import io.grpc.testing.integration.Messages;
import io.grpc.testing.integration.QuarkusTestServiceGrpc;
import io.grpc.testing.integration.TestServiceGrpc;
import io.quarkus.grpc.server.services.MutinyHelloService;
import io.quarkus.grpc.server.services.MutinyTestService;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

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
