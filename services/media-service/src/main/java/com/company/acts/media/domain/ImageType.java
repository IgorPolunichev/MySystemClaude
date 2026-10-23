package com.company.acts.media.domain;

import com.company.acts.common.core.BusinessRuleException;

/** Поддерживаемые форматы изображений; определяются по содержимому файла, а не по расширению. */
public enum ImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png");

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** Формат по сигнатуре файла (magic bytes). */
    public static ImageType detect(byte[] content) {
        if (content.length >= 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (content.length >= 8 && (content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N'
                && content[3] == 'G') {
            return PNG;
        }
        throw new BusinessRuleException("file", "Поддерживаются фото в форматах JPEG и PNG");
    }
}
