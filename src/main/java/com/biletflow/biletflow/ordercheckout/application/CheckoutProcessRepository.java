package com.biletflow.biletflow.ordercheckout.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutProcessRepository {
    Optional<CheckoutProcess> find(UUID id);
    void save(CheckoutProcess process);
    List<UUID> active();
}
