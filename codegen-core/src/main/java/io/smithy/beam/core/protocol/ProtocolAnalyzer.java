package io.smithy.beam.core.protocol;

import io.smithy.beam.core.ir.OperationSpec;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

public interface ProtocolAnalyzer {

    ShapeId getProtocol();

    OperationSpec analyzeClientOperation(OperationShape op, Model model, ServiceShape service);

    default OperationSpec analyzeServerOperation(OperationShape op, Model model, ServiceShape service) {
        throw new UnsupportedOperationException("Server generation not supported for: " + getProtocol());
    }

    String contentType(ServiceShape service);

    default boolean requiresXmlRuntime() {
        return false;
    }

    default boolean requiresQueryRuntime() {
        return false;
    }

    default boolean requiresS3Runtime(ServiceShape service) {
        return false;
    }
}
