package io.quarkus.grpc.runtime.devmode;

import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.runtime.StreamCollector;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 4/29/20
 */
public class DevModeStreamsCollector implements StreamCollector {
    private Set<StreamObserver> streamObservers = new HashSet<>();

    @Override
    public <O> void add(StreamObserver<O> observer) {
        streamObservers.add(observer);
    }

    @Override
    public <O> void remove(StreamObserver<O> observer) {
        streamObservers.remove(observer);
    }

    public void shutdown() {
        streamObservers.forEach(StreamObserver::onCompleted);
    }
}
