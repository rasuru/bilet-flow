package com.biletflow.biletflow.eventmanagement.application.cancellation;

public interface CancellationNoticeSender {
    void send(CancellationNotice notice);
}
