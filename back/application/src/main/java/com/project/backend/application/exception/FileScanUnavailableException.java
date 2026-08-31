package com.project.backend.application.exception;

public final class FileScanUnavailableException extends ApplicationException {
    public FileScanUnavailableException() {
        super("The malware scanner is temporarily unavailable");
    }
}
