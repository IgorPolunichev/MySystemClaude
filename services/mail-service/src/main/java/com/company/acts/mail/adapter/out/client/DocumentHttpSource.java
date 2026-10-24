package com.company.acts.mail.adapter.out.client;

import com.company.acts.mail.application.port.out.AttachmentSource;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Excel акта из document-service (формируется заново — всегда соответствует текущему акту). */
@Component
class DocumentHttpSource implements AttachmentSource {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final RestClient client;

    DocumentHttpSource(@Value("${app.clients.document-url:http://localhost:8085}") String baseUrl) {
        this.client = BearerRelay.client(baseUrl, Duration.ofMinutes(2));
    }

    @Override
    public File actWorkbook(UUID actId) {
        ResponseEntity<byte[]> response = client.get().uri("/api/v1/documents/acts/{id}/xlsx", actId).retrieve()
                .toEntity(byte[].class);
        String name = response.getHeaders().getContentDisposition().getFilename();
        byte[] body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("document-service вернул пустой файл для акта " + actId);
        }
        return new File(name != null ? name : actId + ".xlsx", XLSX, body);
    }
}
