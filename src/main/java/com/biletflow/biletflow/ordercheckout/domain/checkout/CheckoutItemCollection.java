package com.biletflow.biletflow.ordercheckout.domain.checkout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CheckoutItemCollection {
    private final List<CheckoutItem> items;

    public CheckoutItemCollection(List<CheckoutItem> items) {
        Objects.requireNonNull(items, "CheckouItems can not be null!");
        this.items = new ArrayList<>(items);
    }

    public CheckoutItemCollection() {
        this.items = new ArrayList<>();
    }

    public List<CheckoutItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(CheckoutItem item) {
        Objects.requireNonNull(item, "CheckoutItem can not be null!");

        items.add(item);
    }

    public void removeItem(CheckoutItem item) {
        Objects.requireNonNull(item, "CheckoutItem can not be null!");

        boolean res = items.remove(item);
        if (!res) {
            throw new IllegalArgumentException("Item does not exist in checkout!");
        }
    }

    // clear
    public void clear() {
        items.clear();
    }

    public int size() {
        return items.size();
    }
}
