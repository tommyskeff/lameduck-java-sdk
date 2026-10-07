package dev.tommyjs.lameduck;

import dev.tommyjs.lameduck.v1.GetStateRequest;
import dev.tommyjs.lameduck.v1.GetStateResponse;
import dev.tommyjs.lameduck.v1.MarkDrainedRequest;
import dev.tommyjs.lameduck.v1.PodState;
import dev.tommyjs.lameduck.v1.SetLoadRequest;
import dev.tommyjs.lameduck.v1.SidecarServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SidecarClient implements AutoCloseable {

    public static final String ADDRESS_ENV = "LAMEDUCK_SIDECAR_ADDRESS";

    private static final long CALL_TIMEOUT_SECONDS = 5;

    private final ManagedChannel channel;

    private final SidecarServiceGrpc.SidecarServiceFutureStub futureStub;

    private final SidecarServiceGrpc.SidecarServiceStub asyncStub;

    private final ScheduledExecutorService scheduler;

    private SidecarClient(ManagedChannel channel) {
        this.channel = channel;
        this.futureStub = SidecarServiceGrpc.newFutureStub(channel);
        this.asyncStub = SidecarServiceGrpc.newStub(channel);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "lameduck-sidecar-watch");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static SidecarClient fromEnvironment() {
        String address = System.getenv(ADDRESS_ENV);
        if (address == null || address.isBlank()) {
            throw new IllegalStateException(ADDRESS_ENV + " is not set; is the pod managed by a DrainSet?");
        }
        return connect(address);
    }

    public static SidecarClient connect(String address) {
        int separator = address.lastIndexOf(':');
        if (separator <= 0 || separator == address.length() - 1) {
            throw new IllegalArgumentException("sidecar address must be host:port, got " + address);
        }
        String host = address.substring(0, separator);
        int port = Integer.parseInt(address.substring(separator + 1));
        return connect(new InetSocketAddress(host, port));
    }

    public static SidecarClient connect(InetSocketAddress address) {
        return wrap(NettyChannelBuilder.forAddress(address).usePlaintext().build());
    }

    public static SidecarClient wrap(ManagedChannel channel) {
        return new SidecarClient(channel);
    }

    public CompletableFuture<PodState> getState() {
        return FutureHelper.toCompletable(stub().getState(GetStateRequest.getDefaultInstance()))
            .thenApply(GetStateResponse::getState);
    }

    public CompletableFuture<Void> setLoad(long load) {
        SetLoadRequest request = SetLoadRequest.newBuilder().setLoad(load).build();
        return FutureHelper.toCompletable(stub().setLoad(request)).thenApply(response -> null);
    }

    public CompletableFuture<Void> markDrained() {
        return FutureHelper.toCompletable(stub().markDrained(MarkDrainedRequest.getDefaultInstance()))
            .thenApply(response -> null);
    }

    public StateSubscription watch(StateListener listener) {
        StateSubscription subscription = new StateSubscription(asyncStub, scheduler, listener);
        subscription.start();
        return subscription;
    }

    public StateSubscription watchDrain(DrainListener listener) {
        return watch(new DrainTracker(listener));
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        channel.shutdownNow();
    }

    private SidecarServiceGrpc.SidecarServiceFutureStub stub() {
        return futureStub.withDeadlineAfter(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

}
