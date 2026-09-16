package com.biletflow.biletflow.ordercheckout.domain.checkout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CheckoutItemCollection {
    private final List<CheckoutItemMode> items;
    private int quantity;

    // constructor
    public CheckoutItemCollection(List<CheckoutItemMode> items) {
        Objects.requireNonNull(items, "CheckouItems can not be null!");
        this.items = new ArrayList<>(items);
        this.quantity = items.size();
    }

    public CheckoutItemCollection() {
        this.items = new ArrayList<>();
        this.quantity = 0;
    }

    // get
    public List<CheckoutItemMode> getItems() {
        return Collections.unmodifiableList(items);
    }

    // add
    public void addItem(CheckoutItemMode item) {
        Objects.requireNonNull(item, "CheckoutItem can not be null!");

        items.add(item);
        quantity++;
    }

    // delete
    public void removeItem(CheckoutItemMode item) {
        Objects.requireNonNull(item, "CheckoutItem can not be null!");

        boolean res = items.remove(item);
        if (res) {
            quantity--;
        }
    }

    // clear
    public void clear() {
        items.clear();
        quantity = 0;
    }
}
