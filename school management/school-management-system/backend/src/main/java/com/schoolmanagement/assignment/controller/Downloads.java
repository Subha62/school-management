package com.schoolmanagement.assignment.controller;

import com.schoolmanagement.assignment.service.FileDownload;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Builds the response for a file download. Every file is sent as an attachment with the type we validated at
 * upload and {@code nosniff}, so a browser never renders an uploaded file inside the application.
 */
final class Downloads {

    private Downloads() {
    }

    static ResponseEntity<InputStreamResource> respond(FileDownload download) {
        MediaType type;
        try {
            type = MediaType.parseMediaType(download.contentType());
        } catch (RuntimeException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.name(), StandardCharsets.UTF_8).build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new InputStreamResource(download.stream()));
    }
}
