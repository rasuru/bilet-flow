package com.biletflow.biletflow.ticketinventory.infrastructure.delivery;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDocument;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.TicketEventDetails;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TicketDocumentRendererTest {

    private final PdfBoxTicketDocumentRenderer renderer = new PdfBoxTicketDocumentRenderer("Asia/Almaty");

    static TicketDocument document(TicketStatus status, boolean cancelled) {
        return document(status, cancelled, UUID.fromString("6b10e2c7-82ac-468e-9aed-cb8cc9c71359"));
    }

    private static TicketDocument document(TicketStatus status, boolean cancelled, UUID code) {
        var now = Instant.parse("2026-10-19T10:00:00Z");
        var ticket = new TicketView(
            UUID.fromString("d3462063-493f-4ea3-a29b-d7b38c572a65"),
            UUID.fromString("759d7349-b708-4567-971d-a8fca2ae6145"),
            UUID.fromString("8371f7be-024a-4d83-ae86-bcb730b513eb"),
            UUID.fromString("25f6ac45-dfe2-4b5e-ba15-82ff126ed824"),
            "attendee@example.com",
            1L,
            UUID.fromString("218dd9b5-dc6a-44a2-8c6f-cef2e672c057"),
            code,
            status,
            now
        );
        return new TicketDocument(
            ticket,
            "Кіру билеті",
            new TicketEventDetails(
                "Қазақша концерт — Ә Ғ Қ Ң Ө Ұ Ү Һ І",
                now,
                now.plusSeconds(3600),
                "Астана Арена",
                "Астана, Тұран даңғылы 48",
                "Section A / Row 2 / Seat 12",
                cancelled
            )
        );
    }

    static String decode(BufferedImage image) throws Exception {
        int width = image.getWidth(),
            height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        // A payload can contain extra finder-like patterns. Scan all candidates rather than
        // letting the single-code reader stop at an incorrect group of three patterns.
        var results = new QRCodeMultiReader().decodeMultiple(
            new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))),
            Map.of(DecodeHintType.TRY_HARDER, true)
        );
        assertEquals(1, results.length, "Expected exactly one decodable admission QR code");
        return results[0].getText();
    }

    @Test
    void pngEncodesExistingAdmissionCode() throws Exception {
        var ticket = document(TicketStatus.VALID, false);
        var image = ImageIO.read(new ByteArrayInputStream(renderer.qr(ticket)));
        assertEquals(ticket.ticket().ticketCode().toString(), decode(image));
    }

    @ParameterizedTest
    @ValueSource(
        strings = { "6b10e2c7-82ac-468e-9aed-cb8cc9c71359", "f641b2b1-9230-99d5-fefb-2f1d49b1a9a9", "8722f726-7661-94bc-0f75-6f14cd6ec9b8" }
    )
    void printedGrayscalePdfScansAndEmbedsUnicodeFont(String code) throws Exception {
        // Include payloads that reproduced premature finder selection by the single-code reader.
        var ticket = document(TicketStatus.VALID, false, UUID.fromString(code));
        try (var pdf = Loader.loadPDF(renderer.pdf(ticket))) {
            assertEquals(1, pdf.getNumberOfPages());
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Қазақша концерт"));
            assertTrue(text.contains("Ә Ғ Қ Ң Ө Ұ Ү Һ І"));
            assertTrue(text.contains(ticket.ticket().attendeeEmail()));
            assertTrue(text.contains(ticket.event().seat()));
            assertTrue(text.contains("15:00 +05:00"));
            assertTrue(text.contains(ticket.ticket().id().toString()));
            for (var name : pdf.getPage(0).getResources().getFontNames())
                assertTrue(pdf.getPage(0).getResources().getFont(name).isEmbedded());
            for (int dpi : List.of(96, 150, 300)) {
                var page = new PDFRenderer(pdf).renderImageWithDPI(0, dpi, ImageType.GRAY);
                assertEquals(code, assertDoesNotThrow(() -> decode(page), "Grayscale PDF at " + dpi + " DPI"));
            }
        }
    }

    @Test
    void invalidTicketPdfContainsStatusWithoutAdmissionCode() throws Exception {
        for (TicketStatus status : List.of(TicketStatus.REFUNDED, TicketStatus.CANCELLED, TicketStatus.REFUND_PENDING)) {
            var ticket = document(status, false);
            assertThrows(IllegalStateException.class, () -> renderer.qr(ticket));
            try (var pdf = Loader.loadPDF(renderer.pdf(ticket))) {
                var text = new PDFTextStripper().getText(pdf);
                assertTrue(text.contains("NOT VALID FOR ADMISSION"));
                assertFalse(text.contains("Present this QR"));
                assertFalse(text.contains(ticket.ticket().ticketCode().toString()));
            }
        }
        assertThrows(IllegalStateException.class, () -> renderer.qr(document(TicketStatus.VALID, true)));
    }
}
