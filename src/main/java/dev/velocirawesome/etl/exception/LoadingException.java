package dev.velocirawesome.etl.exception;

public class LoadingException extends EtlException {

    public LoadingException(String message) {
        super(message);
    }

    public LoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}
