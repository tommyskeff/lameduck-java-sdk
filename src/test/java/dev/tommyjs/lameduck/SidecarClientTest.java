package dev.tommyjs.lameduck;

import dev.tommyjs.lameduck.v1.MarkDrainedRequest;
import dev.tommyjs.lameduck.v1.MarkDrainedResponse;
import dev.tommyjs.lameduck.v1.Phase;
import dev.tommyjs.lameduck.v1.PodState;
import dev.tommyjs.lameduck.v1.SetLoadRequest;
import dev.tommyjs.lameduck.v1.SetLoadResponse;
import dev.tommyjs.lameduck.v1.SidecarServiceGrpc;
import dev.tommyjs.lameduck.v1.WatchStateRequest;
import dev.tommyjs.lameduck.v1.WatchStateResponse;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SidecarClientTest {

    private final FakeSidecarService service = new FakeSidecarService();

    private Server server;

    private SidecarClient client;

    @BeforeEach
    public void start() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start();
        client = SidecarClient.wrap(InProcessChannelBuilder.forName(name).directExecutor().build());
    }

    @AfterEach
    public void stop() {
        client.close();
        server.shutdownNow();
    }

    @Test
    public void rejectsAddressWithoutPort() {
        assertThrows(IllegalArgumentException.class, () -> SidecarClient.connect("127.0.0.1"));
    }

    @Test
    public void connectsOverTcp() throws Exception {
        Server tcpServer = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
            .addService(service)
            .build()
            .start();
        try (SidecarClient tcpClient = SidecarClient.connect("127.0.0.1:" + tcpServer.getPort())) {
            tcpClient.setLoad(3).get(5, TimeUnit.SECONDS);
        } finally {
            tcpServer.shutdownNow();
        }

        assertEquals(3, service.load.get());
    }

    @Test
    public void setsLoad() throws Exception {
        client.setLoad(12).get(5, TimeUnit.SECONDS);

        assertEquals(12, service.load.get());
    }

    @Test
    public void surfacesFailedPrecondition() {
        ExecutionException exception = assertThrows(
            ExecutionException.class,
            () -> client.markDrained().get(5, TimeUnit.SECONDS)
        );

        StatusRuntimeException status = assertInstanceOf(StatusRuntimeException.class, exception.getCause());
        assertEquals(Status.Code.FAILED_PRECONDITION, status.getStatus().getCode());
    }

    @Test
    public void resubscribesAfterStreamEnds() throws Exception {
        BlockingQueue<PodState> states = new LinkedBlockingQueue<>();

        try (StateSubscription subscription = client.watch(states::add)) {
            assertEquals(Phase.PHASE_STARTING, states.poll(5, TimeUnit.SECONDS).getPhase());
            assertEquals(Phase.PHASE_STARTING, states.poll(5, TimeUnit.SECONDS).getPhase());
        }

        assertEquals(2, service.watches.get(), "watch should have reconnected once");
    }

    private static final class FakeSidecarService extends SidecarServiceGrpc.SidecarServiceImplBase {

        private final AtomicLong load = new AtomicLong(-1);

        private final AtomicInteger watches = new AtomicInteger();

        @Override
        public void setLoad(SetLoadRequest request, StreamObserver<SetLoadResponse> responseObserver) {
            load.set(request.getLoad());
            responseObserver.onNext(SetLoadResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void markDrained(MarkDrainedRequest request, StreamObserver<MarkDrainedResponse> responseObserver) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("pod is not draining").asRuntimeException());
        }

        @Override
        public void watchState(WatchStateRequest request, StreamObserver<WatchStateResponse> responseObserver) {
            int count = watches.incrementAndGet();
            PodState state = PodState.newBuilder().setPhase(Phase.PHASE_STARTING).build();
            responseObserver.onNext(WatchStateResponse.newBuilder().setState(state).build());
            if (count == 1) {
                responseObserver.onCompleted();
            }
        }

    }

}
