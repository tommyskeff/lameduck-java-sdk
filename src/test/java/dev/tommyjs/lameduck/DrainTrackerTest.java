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

    private static PodState starting() {
        return PodState.newBuilder().setPhase(Phase.PHASE_STARTING).build();
    }

    private static PodState unspecified() {
        return PodState.getDefaultInstance();
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

        tracker.onState(starting());
        tracker.onState(active());

        assertEquals(List.of("state PHASE_STARTING", "state PHASE_ACTIVE"), listener.states);
        assertEquals(List.of(), listener.events);
    }

    @Test
    public void ignoresUnspecifiedPhase() {
        RecordingDrainListener listener = new RecordingDrainListener();
        DrainTracker tracker = new DrainTracker(listener);

        tracker.onState(draining(100));
        tracker.onState(unspecified());
        tracker.onState(draining(100));

        assertEquals(List.of("drain DRAIN_REASON_ROLLOUT 100"), listener.events);
        assertEquals(List.of("state PHASE_DRAINING", "state PHASE_DRAINING"), listener.states);
    }

    @Test
    public void ignoresStartingAfterDraining() {
        RecordingDrainListener listener = new RecordingDrainListener();
        DrainTracker tracker = new DrainTracker(listener);

        tracker.onState(draining(100));
        tracker.onState(starting());
        tracker.onState(draining(100));

        assertEquals(List.of("drain DRAIN_REASON_ROLLOUT 100"), listener.events);
        assertEquals(List.of("state PHASE_DRAINING", "state PHASE_DRAINING"), listener.states);
    }

    @Test
    public void cancelsOnlyOnActiveAfterDraining() {
        RecordingDrainListener listener = new RecordingDrainListener();
        DrainTracker tracker = new DrainTracker(listener);

        tracker.onState(draining(100));
        tracker.onState(starting());
        tracker.onState(unspecified());
        tracker.onState(active());

        assertEquals(List.of("drain DRAIN_REASON_ROLLOUT 100", "cancelled"), listener.events);
    }

    @Test
    public void ignoresStartingAfterActive() {
        RecordingDrainListener listener = new RecordingDrainListener();
        DrainTracker tracker = new DrainTracker(listener);

        tracker.onState(active());
        tracker.onState(starting());
        tracker.onState(draining(100));

        assertEquals(List.of("state PHASE_ACTIVE", "state PHASE_DRAINING"), listener.states);
        assertEquals(List.of("drain DRAIN_REASON_ROLLOUT 100"), listener.events);
    }

    private static final class RecordingDrainListener implements DrainListener {

        private final List<String> events = new ArrayList<>();

        private final List<String> states = new ArrayList<>();

        @Override
        public void onState(PodState state) {
            states.add("state " + state.getPhase());
        }

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
