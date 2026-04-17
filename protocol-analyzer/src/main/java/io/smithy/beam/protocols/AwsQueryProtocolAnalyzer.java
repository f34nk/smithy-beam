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
 * {@code aws.protocols#awsQuery} protocol analyzer.
 *
 * <p>All operations are {@code POST /} with {@code Content-Type: application/x-www-form-urlencoded}.
 * All input members go into the form-encoded body; the {@code Action} (operation name) and
 * {@code Version} (service API version) parameters are supplied by the generated
 * {@code make_*_request} functions via {@code smithy_query:encode/2,3}.
 * Responses and errors are returned as XML.
 */
public class AwsQueryProtocolAnalyzer implements ProtocolAnalyzer {

    private static final ShapeId PROTOCOL = ShapeId.from("aws.protocols#awsQuery");

    /** Subclasses may override to supply a different protocol ShapeId (e.g. ec2Query). */
    protected ShapeId protocol() {
        return PROTOCOL;
    }

    @Override
    public final ShapeId getProtocol() {
        return protocol();
    }

    @Override
    public String contentType(ServiceShape service) {
        return "application/x-www-form-urlencoded";
    }

    @Override
    public ErrorCodeStrategy errorStrategy(ServiceShape service) {
        return ErrorCodeStrategy.AWS_QUERY;
    }

    @Override
    public boolean requiresXmlRuntime() {
        return true;
    }

    @Override
    public boolean requiresQueryRuntime() {
        return true;
    }

    @Override
    public OperationSpec analyzeClientOperation(OperationShape op, Model model, ServiceShape service) {
        HttpSpec http = new HttpSpec("POST", "/", 200);

        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        List<String> bodyMembers = new ArrayList<>();
        for (MemberShape member : input.getAllMembers().values()) {
            bodyMembers.add(member.getMemberName());
        }
        BodySpec body = new BodySpec(BodyEncoding.FORM_URLENCODED, bodyMembers, null);

        ErrorSpec errors = RestJsonProtocolAnalyzer.buildErrors(op, model, ErrorCodeStrategy.AWS_QUERY);
        AuthSpec auth = RestJsonProtocolAnalyzer.buildAuth(service);
        PaginationSpec pagination = RestJsonProtocolAnalyzer.buildPagination(op);

        return new OperationSpec(
                op.getId().getName(),
                service.getId().getName(),
                Role.CLIENT,
                http,
                List.<LabelBinding>of(),
                List.<QueryBinding>of(),
                List.<HeaderBinding>of(),
                body,
                errors,
                auth,
                RetrySpec.defaultRetry(),
                pagination,
                RestJsonProtocolAnalyzer.outputTypeName(op, model),
                RestJsonProtocolAnalyzer.inputTypeName(op, model),
                BodyEncoding.XML,                          // AwsQuery responses are XML
                "application/x-www-form-urlencoded",
                ErrorCodeStrategy.AWS_QUERY,
                service.getVersion(),
                null, null, List.of());
    }
}
