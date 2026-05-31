package io.smithy.beam.core;

import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.Optional;

public record BeamSigV4Metadata(String signingName, String signingRegion) {

    public static Optional<BeamSigV4Metadata> from(ServiceShape service) {
        if (!service.hasTrait(SigV4Trait.class)) {
            return Optional.empty();
        }
        SigV4Trait trait = service.expectTrait(SigV4Trait.class);
        return Optional.of(new BeamSigV4Metadata(
                emptyToDefault(trait.getName(), service.getId().getName()),
                "us-east-1"));
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
