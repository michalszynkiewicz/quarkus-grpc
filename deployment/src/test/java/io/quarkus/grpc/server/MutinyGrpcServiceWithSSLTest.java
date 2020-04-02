package io.quarkus.grpc.server;

import com.google.protobuf.EmptyProtos;
import io.grpc.examples.helloworld.*;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.testing.integration.Messages;
import io.grpc.testing.integration.QuarkusTestServiceGrpc;
import io.grpc.testing.integration.TestServiceGrpc;
import io.netty.handler.ssl.SslContext;
import io.quarkus.grpc.server.services.HelloService;
import io.quarkus.grpc.server.services.MutinyHelloService;
import io.quarkus.grpc.server.services.MutinyTestService;
import io.quarkus.grpc.server.services.TestService;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Test services exposed by the gRPC server implemented using the regular gRPC model.
 * Communication use plain-text.
 */
public class MutinyGrpcServiceWithSSLTest extends GrpcServiceTestBase {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().setArchiveProducer(
            () -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MutinyHelloService.class, MutinyTestService.class,
                            GreeterGrpc.class, HelloRequest.class, HelloReply.class, QuarkusGreeterGrpc.class,
                            HelloRequestOrBuilder.class, HelloReplyOrBuilder.class,
                            EmptyProtos.class, Messages.class, QuarkusTestServiceGrpc.class,
                            TestServiceGrpc.class))
            .withConfigurationResource("grpc-server-tls-configuration.properties");

    @Override
    @BeforeEach
    public void init() throws Exception {
        SslContext sslcontext = GrpcSslContexts.forClient()
                .trustManager(createTrustAllTrustManager())
                .build();
        channel = NettyChannelBuilder.forAddress("localhost", 9000)
                .sslContext(sslcontext)
                .build();
    }

    // Create a TrustManager which trusts everything
    private static TrustManager createTrustAllTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

}
