package com.biletflow.biletflow.iam.application.organizer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.security.CurrentActor;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrganizerProfileServiceTest {
    @Test
    void updatesProfileThroughApplicationRepositoryPort() {
        CurrentActor actor = () -> Optional.of(91L);
        OrganizerProfileRepository profiles = mock(OrganizerProfileRepository.class);
        when(profiles.organizerExists(91L)).thenReturn(true);
        when(profiles.save(eq(91L), any())).thenReturn(new OrganizerProfileView(91L, "Astana Events",
            "A. Organizer", "contact@example.com", "+7 700 000 00 00", "https://example.com",
            "Astana", "Public organizer contact information", null));
        OrganizerProfileService service = new OrganizerProfileService(actor, profiles);

        var result = service.update(new UpdateOrganizerProfileCommand(
            "Astana Events", "A. Organizer", "CONTACT@EXAMPLE.COM", "+7 700 000 00 00",
            "https://example.com", "Astana", "Public organizer contact information"));

        assertEquals(91L, result.organizerId());
        assertEquals("Astana Events", result.organizationName());
        verify(profiles).save(eq(91L), any());
    }

    @Test
    void publicProfileDoesNotExposePrivateContactDetails() {
        CurrentActor actor = () -> Optional.of(91L);
        OrganizerProfileRepository profiles = mock(OrganizerProfileRepository.class);
        when(profiles.organizerExists(92L)).thenReturn(true);
        when(profiles.findByOrganizerId(92L)).thenReturn(Optional.of(new OrganizerProfileView(92L, "Public Org",
            "Private Name", "private@example.com", "+7 700 000 00 00", "https://example.com",
            "Astana", "Description", null)));
        var result = new OrganizerProfileService(actor, profiles).getPublic(92L);
        assertEquals("Public Org", result.organizationName());
        assertEquals("https://example.com", result.website());
    }
}
