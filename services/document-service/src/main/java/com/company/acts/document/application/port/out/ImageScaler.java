package com.company.acts.document.application.port.out;

/** Уменьшение фото для документа, чтобы файл не весил десятки мегабайт. */
public interface ImageScaler {

    record Scaled(byte[] jpeg, int width, int height) {
    }

    Scaled fit(byte[] image, int maxSide);
}
