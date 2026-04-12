package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.AuthSpec;
import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.ErrorSpec;
import io.smithy.beam.core.ir.HttpSpec;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.RetrySpec;
import io.smithy.beam.core.ir.Role;
import io.smithy.beam.core.protocol.ProtocolAnalyzer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.List;

/**
 * {@code aws.protocols#restJson1} analyzer. Stub: returns minimal {@link OperationSpec} until RestJson binding
 * logic is implemented (Phase 7).
 */
public final class RestJsonProtocolAnalyzer implements ProtocolAnalyzer {

    private static final ShapeId PROTOCOL = ShapeId.from("aws.protocols#restJson1");

    public RestJsonProtocolAnalyzer() {}

    @Override
    public ShapeId getProtocol() {
        return PROTOCOL;
    }

    @Override
    public OperationSpec analyzeClientOperation(OperationShape op, Model model, ServiceShape service) {
        return minimalSpec(op, service, Role.CLIENT);
    }

    @Override
    public OperationSpec analyzeServerOperation(OperationShape op, Model model, ServiceShape service) {
        return minimalSpec(op, service, Role.SERVER);
    }

    @Override
    public String contentType(ServiceShape service) {
        return "application/json";
    }

    private static OperationSpec minimalSpec(OperationShape op, ServiceShape service, Role role) {
        return new OperationSpec(
                op.getId().getName(),
                service.getId().getName(),
                role,
                new HttpSpec("GET", "/", 200),
                List.of(),
                List.of(),
                List.of(),
                new BodySpec(BodyEncoding.NONE, List.of(), null),
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(),
                RetrySpec.defaultRetry(),
                null);
    }
}
