package dev.tommyjs.lameduck;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.CompletableFuture;

final class FutureHelper {

    private FutureHelper() {
    }

    static <T> CompletableFuture<T> toCompletable(ListenableFuture<T> future) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(T value) {
                result.complete(value);
            }

            @Override
            public void onFailure(Throwable error) {
                result.completeExceptionally(error);
            }
        }, MoreExecutors.directExecutor());
        return result;
    }

}
