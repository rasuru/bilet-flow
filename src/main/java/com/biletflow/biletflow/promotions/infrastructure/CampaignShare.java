package com.biletflow.biletflow.promotions.infrastructure;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CampaignShare {

    private static final int SIZE = 480;
    private static final int BORDER = 16;
    private static final Color BLUE = new Color(0x173B68);

    private final URI origin;

    public CampaignShare(@Value("${biletflow.frontend}") URI origin) {
        if (
            !supportedScheme(origin) ||
            origin.getHost() == null ||
            origin.getRawUserInfo() != null ||
            origin.getRawQuery() != null ||
            origin.getRawFragment() != null ||
            !(origin.getRawPath().isEmpty() || "/".equals(origin.getRawPath()))
        ) throw new IllegalArgumentException("BILETFLOW_FRONTEND must be an HTTPS origin or an HTTP localhost origin");
        this.origin = origin;
    }

    private static boolean supportedScheme(URI origin) {
        if ("https".equalsIgnoreCase(origin.getScheme())) return true;
        String host = origin.getHost();
        return "http".equalsIgnoreCase(origin.getScheme()) &&
            ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host));
    }

    public URI link(UUID eventId, UUID token) {
        return origin.resolve("/events/" + eventId + "?promo=" + token);
    }

    public byte[] qr(URI link) {
        try (var output = new ByteArrayOutputStream()) {
            var matrix = new QRCodeWriter().encode(
                link.toASCIIString(),
                BarcodeFormat.QR_CODE,
                SIZE,
                SIZE,
                Map.of(EncodeHintType.MARGIN, 4, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            );
            var image = new BufferedImage(SIZE + 2 * BORDER, SIZE + 2 * BORDER, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
                graphics.setColor(BLUE);
                graphics.fillRect(0, 0, image.getWidth(), BORDER);
                graphics.fillRect(0, image.getHeight() - BORDER, image.getWidth(), BORDER);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, BORDER, image.getWidth(), SIZE);
            } finally {
                graphics.dispose();
            }
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) image.setRGB(x + BORDER, y + BORDER, BLUE.getRGB());
                }
            }
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException | WriterException failure) {
            throw new IllegalStateException("Campaign QR could not be rendered", failure);
        }
    }
}
