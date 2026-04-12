package io.smithy.beam.core.ir;

public record RetrySpec(boolean enabled, int maxRetries) {
    public static RetrySpec disabled() {
        return new RetrySpec(false, 0);
    }

    public static RetrySpec defaultRetry() {
        return new RetrySpec(true, 3);
    }
}
