package com.company.acts.mail.application.port.out;

import java.util.UUID;

/** Excel-файл акта из document-service. */
public interface AttachmentSource {

    record File(String name, String contentType, byte[] content) {
    }

    File actWorkbook(UUID actId);
}
