package com.biletflow.biletflow.common.events;

/** Durable, transactional publication. The caller's database transaction must be active. */
public interface EventBus {
    void publish(IntegrationEvent event);
}
