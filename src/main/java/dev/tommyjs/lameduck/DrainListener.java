package dev.tommyjs.lameduck;

import dev.tommyjs.lameduck.v1.DrainReason;
import dev.tommyjs.lameduck.v1.PodState;

import java.time.Instant;

public interface DrainListener {

    void onDrain(DrainReason reason, Instant deadline);

    void onDrainCancelled();

    default void onState(PodState state) {
    }

    default void onError(Throwable error) {
    }

}
