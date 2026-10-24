package com.company.acts.document.adapter.out.image;

import com.company.acts.document.application.port.out.ImageScaler;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/** Уменьшение фото для документа средствами JDK; результат — JPEG. */
@Component
class ImageIoScaler implements ImageScaler {

    @Override
    public Scaled fit(byte[] image, int maxSide) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(image));
            if (source == null) {
                throw new IllegalArgumentException("Не удалось прочитать фото");
            }
            double scale = Math.min(1.0, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = target.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);
                g.drawImage(source, 0, 0, width, height, null);
            } finally {
                g.dispose();
            }
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(stream);
                ImageWriteParam params = writer.getDefaultWriteParam();
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(0.85f);
                writer.write(null, new IIOImage(target, null, null), params);
            } finally {
                writer.dispose();
            }
            return new Scaled(out.toByteArray(), width, height);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
