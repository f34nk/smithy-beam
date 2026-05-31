package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

public final class BeamAwsQueryFormEncoder {

    private BeamAwsQueryFormEncoder() {}

    public static String operationAction(OperationShape operation, ServiceShape service) {
        return operation.getId().getName(service);
    }

    public static String serviceVersion(ServiceShape service) {
        return service.getVersion();
    }
}
