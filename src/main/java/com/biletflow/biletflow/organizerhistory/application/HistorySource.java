package com.biletflow.biletflow.organizerhistory.application;

import java.util.List;

public interface HistorySource {
    String name();

    long count(HistoryQuery query);

    List<HistoryEntry> newest(HistoryQuery query, int limit);
}
