package dev.tommyjs.lameduck;

import dev.tommyjs.lameduck.v1.PodState;

public interface StateListener {

    void onState(PodState state);

    default void onError(Throwable error) {
    }

}
