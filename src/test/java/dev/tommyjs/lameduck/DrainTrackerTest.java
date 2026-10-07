package dev.tommyjs.lameduck;

import com.google.protobuf.Timestamp;
import dev.tommyjs.lameduck.v1.DrainReason;
import dev.tommyjs.lameduck.v1.Phase;
import dev.tommyjs.lameduck.v1.PodState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DrainTrackerTest {

    private static PodState active() {
        return PodState.newBuilder().setPhase(Phase.PHASE_ACTIVE).build();
    }

    private static PodState draining(long deadline) {
        return PodState.newBuilder()
            .setPhase(Phase.PHASE_DRAINING)
            .setDrainReason(DrainReason.DRAIN_REASON_ROLLOUT)
            .setDrainDeadline(Timestamp.newBuilder().setSeconds(deadline))
            .build();
    }

    @Test
    public void reportsDrainOnceAndCancellation() {
        RecordingDrainListener listener = new RecordingDrainListener();
        DrainTracker tracker = new DrainTracker(listener);

        tracker.onState(active());
        tracker.onState(draining(100));
        tracker.onState(draining(100));
        tracker.onState(active());

        assertEquals(List.of("drain DRAIN_REASON_ROLLOUT 100", "cancelled"), listener.events);
    }

    @Test
    public void reportsMovedDeadline() {
        RecordingDrainListener listener = new RecordingDrainListener();
        DrainTracker tracker = new DrainTracker(listener);

        tracker.onState(draining(100));
        tracker.onState(draining(50));

        assertEquals(List.of("drain DRAIN_REASON_ROLLOUT 100", "drain DRAIN_REASON_ROLLOUT 50"), listener.events);
    }

    @Test
    public void ignoresStartingPod() {
        RecordingDrainListener listener = new RecordingDrainListener();
        DrainTracker tracker = new DrainTracker(listener);

        tracker.onState(PodState.newBuilder().setPhase(Phase.PHASE_STARTING).build());
        tracker.onState(active());

        assertEquals(List.of(), listener.events);
    }

    private static final class RecordingDrainListener implements DrainListener {

        private final List<String> events = new ArrayList<>();

        @Override
        public void onDrain(DrainReason reason, Instant deadline) {
            events.add("drain " + reason + " " + deadline.getEpochSecond());
        }

        @Override
        public void onDrainCancelled() {
            events.add("cancelled");
        }

    }

}
