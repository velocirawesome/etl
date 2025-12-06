package dev.velocirawesome.etl.exception;

public class ExtractionException extends EtlException {

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
