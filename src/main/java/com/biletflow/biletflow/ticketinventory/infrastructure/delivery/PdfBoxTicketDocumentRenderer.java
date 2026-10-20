package com.biletflow.biletflow.ticketinventory.infrastructure.delivery;

import com.biletflow.biletflow.ticketinventory.application.delivery.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PdfBoxTicketDocumentRenderer implements TicketDocumentRenderer {

    private static final String FONT = "/liberation/LiberationSans-Regular.ttf";
    private final ZoneId zone;

    public PdfBoxTicketDocumentRenderer(@Value("${biletflow.tickets.zone-id:Asia/Almaty}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    private BufferedImage image(TicketDocument ticket) {
        if (!ticket.hasAdmissionCode()) throw new IllegalStateException("This ticket has no usable admission QR code");
        try {
            var matrix = new QRCodeWriter().encode(
                ticket.ticket().ticketCode().toString(),
                BarcodeFormat.QR_CODE,
                512,
                512,
                Map.of(EncodeHintType.MARGIN, 4, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            );
            var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < matrix.getHeight(); y++) for (int x = 0; x < matrix.getWidth(); x++) image.setRGB(
                x,
                y,
                matrix.get(x, y) ? 0x000000 : 0xffffff
            );
            return image;
        } catch (com.google.zxing.WriterException failure) {
            throw new IllegalStateException("Ticket QR could not be rendered", failure);
        }
    }

    @Override
    public byte[] qr(TicketDocument document) {
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image(document), "png", output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Ticket QR could not be rendered", failure);
        }
    }

    @Override
    public byte[] pdf(TicketDocument ticket) {
        try (
            var document = new PDDocument();
            var output = new ByteArrayOutputStream();
            var fontStream = getClass().getResourceAsStream(FONT)
        ) {
            if (fontStream == null) throw new IllegalStateException("Bundled ticket font is missing");
            var font = PDType0Font.load(document, fontStream);
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            document.getDocumentInformation().setTitle("BiletFlow ticket " + ticket.ticket().id());
            try (var content = new PDPageContentStream(document, page)) {
                float y = lines(content, font, "BiletFlow", 24, 38, 793, 510, 1);
                y = lines(content, font, ticket.event().title(), 18, 38, y - 16, 510, 3);
                String status = ticket.event().cancelled() ? "EVENT CANCELLED" : ticket.ticket().status().name().replace('_', ' ');
                y = lines(content, font, "Ticket status: " + status, 12, 38, y - 10, 510, 1);
                var date = DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm XXX", Locale.ENGLISH).withZone(zone);
                y = lines(content, font, "Starts: " + date.format(ticket.event().startsAt()), 11, 38, y - 12, 510, 1);
                y = lines(content, font, "Ends: " + date.format(ticket.event().endsAt()) + " (" + zone + ")", 11, 38, y, 510, 1);
                y = lines(content, font, "Venue: " + ticket.event().venue(), 11, 38, y - 10, 510, 2);
                y = lines(content, font, "Address: " + ticket.event().address(), 11, 38, y, 510, 3);
                y = lines(content, font, "Ticket type: " + ticket.ticketType(), 11, 38, y - 10, 510, 2);
                y = lines(content, font, "Attendee email: " + ticket.ticket().attendeeEmail(), 11, 38, y, 510, 2);
                y = lines(content, font, "Seat: " + ticket.event().seat(), 11, 38, y, 510, 2);
                y = lines(content, font, "Ticket ID: " + ticket.ticket().id(), 10, 38, y - 10, 510, 1);
                lines(content, font, "Order reference: " + ticket.ticket().orderId(), 10, 38, y, 510, 1);
                if (ticket.hasAdmissionCode()) {
                    content.drawImage(LosslessFactory.createFromImage(document, image(ticket)), 38, 72, 200, 200);
                    lines(content, font, ticket.ticket().ticketCode().toString(), 8, 38, 58, 240, 1);
                } else lines(content, font, "NOT VALID FOR ADMISSION", 16, 38, 215, 510, 2);
                lines(
                    content,
                    font,
                    ticket.hasAdmissionCode()
                        ? "Present this QR on your phone or print at actual size. Black-and-white printing is supported. Digital and printed copies share one admission. Keep this code private. Admission status is verified online; this PDF does not override cancellations or refunds."
                        : "This document is not valid for admission. Do not use older printed or digital copies. Check your account for the current ticket or refund status.",
                    10,
                    260,
                    245,
                    285,
                    9
                );
                lines(
                    content,
                    font,
                    "BiletFlow demonstration platform - this ticket is not evidence of a real-money payment.",
                    9,
                    38,
                    32,
                    510,
                    2
                );
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Ticket PDF could not be rendered", failure);
        }
    }

    private float lines(
        PDPageContentStream content,
        PDType0Font font,
        String value,
        float size,
        float x,
        float y,
        float width,
        int maxLines
    ) throws IOException {
        var clean = new StringBuilder();
        for (int cp : value.codePoints().toArray()) {
            String glyph = Character.isISOControl(cp) || Character.isWhitespace(cp) ? " " : new String(Character.toChars(cp));
            try {
                font.encode(glyph);
                clean.append(glyph);
            } catch (IllegalArgumentException unsupported) {
                clean.append('?');
            }
        }
        String remaining = clean.toString().trim();
        for (int line = 0; line < maxLines && !remaining.isEmpty(); line++) {
            int end = 0;
            while (end < remaining.length()) {
                int next = remaining.offsetByCodePoints(end, 1);
                if ((font.getStringWidth(remaining.substring(0, next)) * size) / 1000 > width) break;
                end = next;
            }
            if (end == 0) end = remaining.offsetByCodePoints(0, 1);
            if (end < remaining.length()) {
                int space = remaining.lastIndexOf(' ', end);
                if (space > 0) end = space;
            }
            String text = remaining.substring(0, end).stripTrailing();
            remaining = remaining.substring(end).stripLeading();
            if (line == maxLines - 1 && !remaining.isEmpty()) {
                while (!text.isEmpty() && (font.getStringWidth(text + "...") * size) / 1000 > width)
                    text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
                text += "...";
            }
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, y);
            content.showText(text);
            content.endText();
            y -= size + 4;
        }
        return y;
    }
}
