package com.company.acts.media.application.port.out;

/** Разбор и уменьшение изображений. */
public interface ImageProcessor {

    record ImageInfo(int width, int height) {
    }

    ImageInfo inspect(byte[] content);

    /** JPEG-превью, вписанное в квадрат maxSide × maxSide с сохранением пропорций. */
    byte[] thumbnail(byte[] content, int maxSide);
}
