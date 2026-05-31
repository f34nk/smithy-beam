package io.smithy.beam.core;

import software.amazon.smithy.aws.traits.ServiceTrait;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.Optional;

public record BeamAwsServiceMetadata(
        String sdkId,
        String endpointPrefix,
        String signingName) {

    public static Optional<BeamAwsServiceMetadata> from(ServiceShape service) {
        if (!service.hasTrait(ServiceTrait.class)) {
            return Optional.empty();
        }
        ServiceTrait trait = service.expectTrait(ServiceTrait.class);
        String sdkId = trait.getSdkId();
        return Optional.of(new BeamAwsServiceMetadata(
                sdkId,
                emptyToDefault(trait.getEndpointPrefix(), sdkId),
                emptyToDefault(trait.getArnNamespace(), sdkId)));
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
