package com.biletflow.biletflow.organizerhistory.application;

import java.util.List;

public record HistoryPage(List<HistoryEntry> entries, int page, int size, long total) {
    public HistoryPage {
        entries = List.copyOf(entries);
    }
}
