package dev.tommyjs.lameduck;

import com.google.protobuf.Timestamp;
import dev.tommyjs.lameduck.v1.Phase;
import dev.tommyjs.lameduck.v1.PodState;

import java.time.Instant;

public final class DrainTracker implements StateListener {

    private final DrainListener listener;

    private Phase phase = Phase.PHASE_UNSPECIFIED;

    private Timestamp deadline = Timestamp.getDefaultInstance();

    public DrainTracker(DrainListener listener) {
        this.listener = listener;
    }

    @Override
    public synchronized void onState(PodState state) {
        Phase next = state.getPhase();
        if (isIgnored(next)) {
            return;
        }
        listener.onState(state);
        Timestamp nextDeadline = state.getDrainDeadline();
        if (next == Phase.PHASE_DRAINING && (phase != Phase.PHASE_DRAINING || !nextDeadline.equals(deadline))) {
            Instant instant = Instant.ofEpochSecond(nextDeadline.getSeconds(), nextDeadline.getNanos());
            listener.onDrain(state.getDrainReason(), instant);
        } else if (next == Phase.PHASE_ACTIVE && phase == Phase.PHASE_DRAINING) {
            listener.onDrainCancelled();
        }
        phase = next;
        deadline = nextDeadline;
    }

    @Override
    public void onError(Throwable error) {
        listener.onError(error);
    }

    private boolean isIgnored(Phase next) {
        return switch (next) {
            case PHASE_STARTING -> phase != Phase.PHASE_UNSPECIFIED && phase != Phase.PHASE_STARTING;
            case PHASE_ACTIVE, PHASE_DRAINING -> false;
            default -> true;
        };
    }

}
