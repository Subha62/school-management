package com.schoolmanagement.assignment.service;

import java.io.InputStream;

/** A file ready to be sent to a client. The caller closes the stream. */
public record FileDownload(String name, String contentType, long sizeBytes, InputStream stream) {
}
