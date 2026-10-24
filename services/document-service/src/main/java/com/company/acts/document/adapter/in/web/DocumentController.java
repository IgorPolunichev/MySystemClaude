package com.company.acts.document.adapter.in.web;

import com.company.acts.document.application.ActDocumentService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
class DocumentController {

    private final ActDocumentService documents;

    DocumentController(ActDocumentService documents) {
        this.documents = documents;
    }

    /** Excel-файл акта: «Акт», «Табель», «Техотчёт 1…N». Формируется заново при каждом запросе. */
    @GetMapping("/acts/{actId}/xlsx")
    ResponseEntity<byte[]> actWorkbook(@PathVariable UUID actId) {
        ActDocumentService.Document doc = documents.actWorkbook(actId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(doc.filename(), StandardCharsets.UTF_8).build().toString())
                .body(doc.content());
    }
}
