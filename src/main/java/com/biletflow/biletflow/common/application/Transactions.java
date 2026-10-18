package com.biletflow.biletflow.common.application;

public interface Transactions {
    <T> T required(java.util.function.Supplier<T> work);

    default void run(Runnable work) {
        required(() -> {
            work.run();
            return null;
        });
    }
}
