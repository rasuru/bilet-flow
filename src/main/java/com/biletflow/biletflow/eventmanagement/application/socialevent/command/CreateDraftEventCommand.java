package com.biletflow.biletflow.eventmanagement.application.socialevent.command;

import com.biletflow.biletflow.eventmanagement.domain.EventDateRange;
import com.biletflow.biletflow.eventmanagement.domain.EventVisibility;
import com.biletflow.biletflow.eventmanagement.domain.RegistrationWindow;
import java.util.Objects;

public record CreateDraftEventCommand(
    String title,
    String description,
    String category,
    String imageUrl,
    EventVisibility visibility,
    EventDateRange dateRange,
    RegistrationWindow registrationWindow
) {
    public CreateDraftEventCommand {
        Objects.requireNonNull(title, "Title cannot be null");
        Objects.requireNonNull(category, "Category cannot be null");
        Objects.requireNonNull(visibility, "Visibility cannot be null");
        Objects.requireNonNull(dateRange, "Date range cannot be null");
        Objects.requireNonNull(registrationWindow, "Registration window cannot be null");
    }
}
