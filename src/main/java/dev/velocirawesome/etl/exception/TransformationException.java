package dev.velocirawesome.etl.exception;

public class TransformationException extends EtlException {

    public TransformationException(String message) {
        super(message);
    }

    public TransformationException(String message, Throwable cause) {
        super(message, cause);
    }
}
