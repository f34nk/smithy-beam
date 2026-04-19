package io.smithy.beam.core;

public final class BeamCodegenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BeamCodegenException(String message) {
        super(message);
    }

    public BeamCodegenException(String message, Throwable cause) {
        super(message, cause);
    }
}
