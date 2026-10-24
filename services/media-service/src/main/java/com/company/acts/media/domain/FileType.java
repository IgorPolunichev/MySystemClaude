package com.company.acts.media.domain;

import com.company.acts.common.core.BusinessRuleException;
import java.util.Locale;

/**
 * Типы файлов, определяемые по содержимому (сигнатуре), а не по расширению. Фото акта — только изображения;
 * документы акта (сканы, письма, протоколы) — также PDF, TIFF и документы Office.
 */
public enum FileType {
    JPEG("image/jpeg", "jpg", true),
    PNG("image/png", "png", true),
    PDF("application/pdf", "pdf", false),
    TIFF("image/tiff", "tif", false),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx", false),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx", false),
    ZIP_OTHER("application/zip", "zip", false);

    private final String contentType;
    private final String extension;
    private final boolean image;

    FileType(String contentType, String extension, boolean image) {
        this.contentType = contentType;
        this.extension = extension;
        this.image = image;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** Есть ли превью (JPEG и PNG). */
    public boolean image() {
        return image;
    }

    public static FileType detect(byte[] c, String filename) {
        if (starts(c, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (starts(c, 0x89, 'P', 'N', 'G')) {
            return PNG;
        }
        if (starts(c, '%', 'P', 'D', 'F')) {
            return PDF;
        }
        if (starts(c, 'I', 'I', 0x2A, 0x00) || starts(c, 'M', 'M', 0x00, 0x2A)) {
            return TIFF;
        }
        if (starts(c, 'P', 'K', 0x03, 0x04)) {
            String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            return name.endsWith(".docx") ? DOCX : name.endsWith(".xlsx") ? XLSX : ZIP_OTHER;
        }
        throw new BusinessRuleException("file",
                "Поддерживаются PDF, JPEG, PNG, TIFF, документы Word и Excel (.docx, .xlsx) и архивы ZIP");
    }

    private static boolean starts(byte[] c, int... signature) {
        if (c.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((c[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
