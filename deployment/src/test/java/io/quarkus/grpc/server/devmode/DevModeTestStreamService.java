package io.quarkus.grpc.server.devmode;

import com.example.test.MutinyStreamsGrpc;
import com.example.test.StreamsOuterClass.Item;
import io.smallrye.mutiny.Multi;

import javax.inject.Singleton;
import java.time.Duration;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 4/29/20
 */
@Singleton
public class DevModeTestStreamService extends MutinyStreamsGrpc.StreamsImplBase {

    public static final String PREFIX = "echo::";

    @Override
    public Multi<Item> echo(Multi<Item> request) {
        return request.flatMap(value ->
                Multi.createFrom().ticks().every(Duration.ofMillis(20))
                        .map(whatever ->
                                Item.newBuilder().setName(PREFIX + value.getName()).build()
                        )
        );
    }
}
