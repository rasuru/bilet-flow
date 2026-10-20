package com.biletflow.biletflow.ticketinventory.infrastructure.delivery;

import static org.junit.jupiter.api.Assertions.*;

import com.biletflow.biletflow.ticketinventory.application.delivery.TicketDocument;
import com.biletflow.biletflow.ticketinventory.application.eventmanagement.port.TicketEventDetails;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import com.biletflow.biletflow.ticketinventory.domain.ticket.TicketStatus;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class TicketDocumentRendererTest {

    private final PdfBoxTicketDocumentRenderer renderer = new PdfBoxTicketDocumentRenderer("Asia/Almaty");

    static TicketDocument document(TicketStatus status, boolean cancelled) {
        var now = Instant.parse("2026-10-19T10:00:00Z");
        var ticket = new TicketView(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "attendee@example.com",
            1L,
            UUID.randomUUID(),
            UUID.randomUUID(),
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
        return new QRCodeReader()
            .decode(
                new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))),
                Map.of(DecodeHintType.TRY_HARDER, true)
            )
            .getText();
    }

    @Test
    void pngEncodesExistingAdmissionCode() throws Exception {
        var ticket = document(TicketStatus.VALID, false);
        var image = ImageIO.read(new ByteArrayInputStream(renderer.qr(ticket)));
        assertEquals(ticket.ticket().ticketCode().toString(), decode(image));
    }

    @Test
    void printedGrayscalePdfScansAndEmbedsUnicodeFont() throws Exception {
        var ticket = document(TicketStatus.VALID, false);
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
            var page = new PDFRenderer(pdf).renderImageWithDPI(0, 150, ImageType.GRAY);
            assertEquals(ticket.ticket().ticketCode().toString(), decode(page));
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
