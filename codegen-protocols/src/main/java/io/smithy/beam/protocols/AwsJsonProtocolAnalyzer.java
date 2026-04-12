package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.AuthSpec;
import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.ErrorSpec;
import io.smithy.beam.core.ir.HeaderBinding;
import io.smithy.beam.core.ir.HttpSpec;
import io.smithy.beam.core.ir.LabelBinding;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.PaginationSpec;
import io.smithy.beam.core.ir.QueryBinding;
import io.smithy.beam.core.ir.RetrySpec;
import io.smithy.beam.core.ir.Role;
import io.smithy.beam.core.protocol.ProtocolAnalyzer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code aws.protocols#awsJson1_0} protocol analyzer.
 *
 * All operations are {@code POST /} with the operation target in an {@code X-Amz-Target} header;
 * there are no URI labels, query parameters, or per-member HTTP header bindings.
 * All input members go into the JSON body.
 */
public class AwsJsonProtocolAnalyzer implements ProtocolAnalyzer {

    private static final ShapeId PROTOCOL = ShapeId.from("aws.protocols#awsJson1_0");
    private static final String CONTENT_TYPE = "application/x-amz-json-1.0";

    /** Subclasses may override to supply a different protocol ShapeId (e.g. awsJson1_1). */
    protected ShapeId protocol() {
        return PROTOCOL;
    }

    /** Subclasses may override to supply a different Content-Type (e.g. awsJson1_1). */
    protected String baseContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public final ShapeId getProtocol() {
        return protocol();
    }

    @Override
    public String contentType(ServiceShape service) {
        return baseContentType();
    }

    @Override
    public OperationSpec analyzeClientOperation(OperationShape op, Model model, ServiceShape service) {
        HttpSpec http = new HttpSpec("POST", "/", 200);

        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        List<String> bodyMembers = new ArrayList<>();
        for (MemberShape member : input.getAllMembers().values()) {
            bodyMembers.add(member.getMemberName());
        }
        BodySpec body = new BodySpec(BodyEncoding.JSON, bodyMembers, null);

        String targetValue = service.getId().getName() + "." + op.getId().getName();
        List<HeaderBinding> headers = List.of(new HeaderBinding("__target__", "X-Amz-Target", true));

        ErrorSpec errors = RestJsonProtocolAnalyzer.buildErrors(op, model, ErrorCodeStrategy.AWS_JSON);
        AuthSpec auth = RestJsonProtocolAnalyzer.buildAuth(service);
        PaginationSpec pagination = RestJsonProtocolAnalyzer.buildPagination(op);

        return new OperationSpec(
                op.getId().getName(),
                service.getId().getName(),
                Role.CLIENT,
                http,
                List.<LabelBinding>of(),
                List.<QueryBinding>of(),
                headers,
                body,
                errors,
                auth,
                RetrySpec.defaultRetry(),
                pagination);
    }
}
