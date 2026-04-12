package io.smithy.beam.core;

/** Unchecked failure during code generation (model, protocol detection, or emit). */
public final class CodegenException extends RuntimeException {

    public CodegenException(String message) {
        super(message);
    }

    public CodegenException(String message, Throwable cause) {
        super(message, cause);
    }
}
