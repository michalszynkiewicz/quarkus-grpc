package io.quarkus.grpc.health;

import grpc.health.v1.HealthGrpc;
import grpc.health.v1.HealthOuterClass;
import grpc.health.v1.HealthOuterClass.HealthCheckResponse.ServingStatus;
import grpc.health.v1.MutinyHealthGrpc;
import io.quarkus.grpc.runtime.annotations.GrpcService;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.restassured.RestAssured.when;
import static java.util.Arrays.asList;
import static java.util.function.Predicate.isEqual;
import static org.awaitility.Awaitility.await;

public class MicroProfileHealthDisabledTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().setArchiveProducer(
            () -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(HealthGrpc.class.getPackage()))
            .withConfigurationResource("no-mp-health-config.properties");

    @Test
    public void shouldNotGetMpHealthInfoWhenDisabled() {
        // @formatter:off
        when()
                .get("/health")
        .then()
                .statusCode(200)
                .body("checks.size()", Matchers.equalTo(0));
        // @formatter:on
    }
}