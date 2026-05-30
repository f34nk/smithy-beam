package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.ServiceShape;

public final class BeamServiceNaming {

    private BeamServiceNaming() {}

    /**
     * Service-scoped shape name per Smithy rename maps and service context.
     */
    public static String effectiveServiceName(ServiceShape service) {
        return service.getId().getName(service);
    }

    public static String effectiveServiceSnakeName(ServiceShape service) {
        return BeamNameUtils.toSnakeCase(effectiveServiceName(service));
    }
}
