package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.Optional;

public final class BeamEndpointResolver {

    private BeamEndpointResolver() {}

    /**
     * Builds the default regional HTTPS URL from aws.api#service endpointPrefix and config region.
     * Does not read smithy.api#endpoint; operation hostPrefix remains per-request in codecs.
     */
    public static Optional<String> defaultRegionalBaseUrl(ServiceShape service, String region) {
        return BeamAwsServiceMetadata.from(service)
                .map(meta -> "https://" + meta.endpointPrefix() + "." + region + ".amazonaws.com");
    }
}
