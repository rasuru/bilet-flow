package com.biletflow.biletflow.eventmanagement.ohs;

import com.biletflow.biletflow.eventmanagement.ohs.dto.EventSeatMapView;
import com.biletflow.biletflow.eventmanagement.ohs.dto.EventTicketingConfigurationView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Published synchronous contract of Event Management. */
public interface EventManagementOhs {
    EventTicketingConfigurationView getTicketingConfiguration(UUID eventId);
    EventSeatMapView getSeatMap(UUID eventId);
    boolean canManageTicketing(UUID eventId, Long userId);
    boolean canCheckIn(UUID eventId, Long userId);
    SalesScope salesScope(UUID eventId, Instant now);
    com.biletflow.biletflow.eventmanagement.ohs.dto.TicketEventDetailsView ticketDetails(UUID eventId, UUID seatId);

    record SupportEvent(String title, Instant startsAt, Instant endsAt, String venue, String address,
                        boolean cancelled, boolean suspended) {}

    /** Support can also inspect an event draft whose venue has not been configured. */
    default SupportEvent supportEvent(UUID eventId) {
        var details = ticketDetails(eventId, null);
        return new SupportEvent(details.title(), details.startsAt(), details.endsAt(), details.venue(),
            details.address(), details.cancelled(), details.suspended());
    }

    /** Whether an organizer or assigned EVENT_ADMIN may handle attendee support for the event. */
    boolean canHandleAttendeeSupport(UUID eventId, Long userId);

    /** Event IDs for which the user is an organizer or assigned EVENT_ADMIN. */
    List<UUID> findSupportManagedEventIds(Long userId);

    /** Routing information used to notify the event's support staff. */
    Optional<SupportRouting> supportRouting(UUID eventId);

    /** Projects support status changes into Event Management's organizer activity timeline. */
    void recordSupportCaseStatusChanged(UUID eventId, UUID caseId, Long actorUserId,
                                        String previousStatus, String newStatus, Instant occurredAt);

    record SalesScope(
        UUID eventId, long organizerId, boolean registrationOpen, boolean cancelled, Instant eventStartsAt, boolean publiclyReadable
    ) {
        public SalesScope(UUID eventId, long organizerId, boolean registrationOpen, boolean cancelled, Instant eventStartsAt) {
            this(eventId, organizerId, registrationOpen, cancelled, eventStartsAt, true);
        }
    }

    record SupportRouting(UUID eventId, Long organizerUserId, List<Long> supportStaffUserIds) {
        public SupportRouting {
            supportStaffUserIds = List.copyOf(supportStaffUserIds);
        }
    }
}

