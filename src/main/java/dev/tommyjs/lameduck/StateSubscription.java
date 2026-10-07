package dev.tommyjs.lameduck;

import dev.tommyjs.lameduck.v1.SidecarServiceGrpc;
import dev.tommyjs.lameduck.v1.WatchStateRequest;
import dev.tommyjs.lameduck.v1.WatchStateResponse;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StateSubscription implements AutoCloseable {

    private static final Duration MIN_BACKOFF = Duration.ofMillis(500);

    private static final Duration MAX_BACKOFF = Duration.ofSeconds(10);

    private final SidecarServiceGrpc.SidecarServiceStub stub;

    private final ScheduledExecutorService scheduler;

    private final StateListener listener;

    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile ClientCallStreamObserver<WatchStateRequest> call;

    private volatile Duration backoff = MIN_BACKOFF;

    StateSubscription(
        SidecarServiceGrpc.SidecarServiceStub stub,
        ScheduledExecutorService scheduler,
        StateListener listener
    ) {
        this.stub = stub;
        this.scheduler = scheduler;
        this.listener = listener;
    }

    void start() {
        if (closed.get()) {
            return;
        }
        stub.withWaitForReady().watchState(WatchStateRequest.getDefaultInstance(), new WatchObserver());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ClientCallStreamObserver<WatchStateRequest> current = call;
        if (current != null) {
            current.cancel("subscription closed", null);
        }
    }

    private void retry() {
        if (closed.get()) {
            return;
        }
        Duration delay = backoff;
        Duration doubled = backoff.multipliedBy(2);
        backoff = doubled.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : doubled;
        try {
            scheduler.schedule(this::start, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException exception) {
            closed.set(true);
        }
    }

    private final class WatchObserver implements ClientResponseObserver<WatchStateRequest, WatchStateResponse> {

        @Override
        public void beforeStart(ClientCallStreamObserver<WatchStateRequest> requestStream) {
            call = requestStream;
        }

        @Override
        public void onNext(WatchStateResponse response) {
            backoff = MIN_BACKOFF;
            listener.onState(response.getState());
        }

        @Override
        public void onError(Throwable error) {
            if (closed.get()) {
                return;
            }
            listener.onError(error);
            retry();
        }

        @Override
        public void onCompleted() {
            retry();
        }

    }

}
