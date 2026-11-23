package com.biletflow.biletflow.iam.application.organizer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.iam.persistence.UserRepository;
import com.biletflow.biletflow.iam.persistence.organizer.OrganizerProfileJpaEntity;
import com.biletflow.biletflow.iam.persistence.organizer.OrganizerProfileJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrganizerProfileServiceTest {

    @Test
    void savesAndReturnsOnlyTheExplicitOrganizerContactFields() {
        CurrentActor actor = () -> Optional.of(91L);
        UserRepository users = mock(UserRepository.class);
        OrganizerProfileJpaRepository profiles = mock(OrganizerProfileJpaRepository.class);
        when(users.existsById(91L)).thenReturn(true);
        when(profiles.findById(91L)).thenReturn(Optional.empty());
        when(profiles.save(any())).thenAnswer(call -> call.getArgument(0));
        OrganizerProfileService service = new OrganizerProfileService(actor, users, profiles,
            Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC));

        var result = service.update(new UpdateOrganizerProfileCommand(
            "Astana Events", "A. Organizer", "CONTACT@EXAMPLE.COM", "+7 700 000 00 00",
            "https://example.com", "Astana", "Public organizer contact information"
        ));

        assertEquals(91L, result.organizerId());
        assertEquals("Astana Events", result.organizationName());
        assertEquals("contact@example.com", result.contactEmail());
        verify(profiles).save(any(OrganizerProfileJpaEntity.class));
    }
}
