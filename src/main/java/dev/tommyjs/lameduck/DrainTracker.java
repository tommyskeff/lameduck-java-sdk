package dev.tommyjs.lameduck;

import com.google.protobuf.Timestamp;
import dev.tommyjs.lameduck.v1.Phase;
import dev.tommyjs.lameduck.v1.PodState;

import java.time.Instant;

public final class DrainTracker implements StateListener {

    private final DrainListener listener;

    private boolean draining;

    private Timestamp deadline = Timestamp.getDefaultInstance();

    public DrainTracker(DrainListener listener) {
        this.listener = listener;
    }

    @Override
    public synchronized void onState(PodState state) {
        listener.onState(state);
        boolean nowDraining = state.getPhase() == Phase.PHASE_DRAINING;
        Timestamp nextDeadline = state.getDrainDeadline();
        if (nowDraining && (!draining || !nextDeadline.equals(deadline))) {
            Instant instant = Instant.ofEpochSecond(nextDeadline.getSeconds(), nextDeadline.getNanos());
            listener.onDrain(state.getDrainReason(), instant);
        } else if (!nowDraining && draining) {
            listener.onDrainCancelled();
        }
        draining = nowDraining;
        deadline = nextDeadline;
    }

    @Override
    public void onError(Throwable error) {
        listener.onError(error);
    }

}
