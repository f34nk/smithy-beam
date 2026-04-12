package io.smithy.beam.core.ir;

public record AuthSpec(boolean requiresSigV4, String signingName) {
    public static AuthSpec none() {
        return new AuthSpec(false, null);
    }
}
