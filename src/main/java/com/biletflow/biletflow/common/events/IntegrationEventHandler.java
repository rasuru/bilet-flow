package com.biletflow.biletflow.common.events;

/** One process-manager consumer per event type. Handling and acknowledgement commit together. */
public interface IntegrationEventHandler {
    String eventType();
    void handle(IntegrationEvent event);
}
