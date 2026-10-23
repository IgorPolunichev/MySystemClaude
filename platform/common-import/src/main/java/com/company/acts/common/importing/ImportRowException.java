package com.company.acts.common.importing;

/** Ошибка в одной строке файла: строка пропускается, импорт продолжается. */
public class ImportRowException extends RuntimeException {

    public ImportRowException(String message) {
        super(message);
    }
}
