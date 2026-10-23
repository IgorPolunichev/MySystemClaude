package com.company.acts.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.test.PostgresIntegrationTest;
import com.company.acts.media.application.MediaService;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Загрузка фото на реальном PostgreSQL; файлы — в хранилище в памяти. */
@SpringBootTest(properties = "app.storage.type=memory")
class MediaScenariosIT extends PostgresIntegrationTest {

    @Autowired
    MediaService media;

    static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    @Test
    void uploadStoresOriginalAndThumbnail() throws Exception {
        UUID actId = UUID.randomUUID();
        var stored = media.upload("photo.jpg", jpeg(2560, 1920), "ACT_PHOTO", actId, "engineer");
        assertThat(stored.width()).isEqualTo(2560);
        assertThat(stored.height()).isEqualTo(1920);
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.objectKey()).startsWith("act_photo/" + actId);

        var thumb = media.content(stored.id(), true);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(thumb.bytes()));
        assertThat(image.getWidth()).isEqualTo(400);
        assertThat(image.getHeight()).isEqualTo(300);
        assertThat(media.content(stored.id(), false).bytes()).hasSizeGreaterThan(thumb.bytes().length);
    }

    @Test
    void rejectsWrongFiles() {
        UUID owner = UUID.randomUUID();
        assertThatThrownBy(() -> media.upload("x.gif", "GIF89a-not-supported".getBytes(), "ACT_PHOTO", owner, "u"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("JPEG");
        assertThatThrownBy(() -> media.upload("x.jpg", new byte[0], "ACT_PHOTO", owner, "u"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> media.upload("x.jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2},
                "ACT_PHOTO", owner, "u")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> media.upload("x.jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
                "UNKNOWN", owner, "u")).isInstanceOf(BusinessRuleException.class);
    }
}
