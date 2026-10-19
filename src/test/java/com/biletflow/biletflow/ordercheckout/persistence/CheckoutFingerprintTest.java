package com.biletflow.biletflow.ordercheckout.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.common.persistence.JsonCodec;
import com.biletflow.biletflow.ordercheckout.application.command.CreateCheckoutCommand;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs.Selection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CheckoutFingerprintTest {

    @Test
    void extractedCommandRetainsLegacyFingerprintEncoding() throws Exception {
        UUID event = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            type = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var command = new CreateCheckoutCommand(event, List.of(new Selection(type, List.of(), List.of("attendee@example.com"))));
        String legacy =
            "{\"eventId\":\"" +
            event +
            "\",\"selections\":[{\"ticketTypeId\":\"" +
            type +
            "\",\"seats\":[],\"attendeeEmails\":[\"attendee@example.com\"]}]}";
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(legacy.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, new JsonCheckoutFingerprint(new JsonCodec(JsonMapper.builder().build())).of(command));
    }
}
