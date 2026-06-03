package io.smithy.beam.core;

import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.RetryableTrait;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.Optional;

/**
 * Reads {@code smithy.api#retryable} metadata for {@code @error} structures.
 */
public final class BeamRetryIndex {

    public record RetryInfo(boolean retryable, boolean throttling) {}

    private BeamRetryIndex() {}

    public static Optional<RetryInfo> forError(StructureShape error) {
        if (!error.hasTrait(ErrorTrait.class)) {
            return Optional.empty();
        }
        RetryableTrait retryable = error.getTrait(RetryableTrait.class).orElse(null);
        if (retryable == null) {
            return Optional.of(new RetryInfo(false, false));
        }
        return Optional.of(new RetryInfo(true, retryable.getThrottling()));
    }
}
