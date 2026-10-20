package com.biletflow.biletflow.common.application;

public interface TransactionLocks {
    void lock(String key);
}
