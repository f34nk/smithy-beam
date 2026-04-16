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
import software.amazon.smithy.aws.traits.ServiceTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpPayloadTrait;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code aws.protocols#restXml} protocol analyzer.
 *
 * <p>Uses the same HTTP binding analysis as {@link RestJsonProtocolAnalyzer} ({@code @http},
 * {@code @httpLabel}, {@code @httpQuery}, {@code @httpHeader}) but serializes the request body
 * as XML rather than JSON.  Requires the {@code aws_xml.erl} runtime module.
 *
 * <p>For S3-like services (where the {@code aws.api#service} trait's {@code arnNamespace} starts
 * with {@code "s3"}), {@link #requiresS3Runtime} returns {@code true} so that the pipeline also
 * copies {@code aws_s3.erl} into the build output.
 */
public final class RestXmlProtocolAnalyzer implements ProtocolAnalyzer {

    private static final ShapeId PROTOCOL = ShapeId.from("aws.protocols#restXml");

    @Override
    public ShapeId getProtocol() {
        return PROTOCOL;
    }

    @Override
    public String contentType(ServiceShape service) {
        return "application/xml";
    }

    @Override
    public boolean requiresXmlRuntime() {
        return true;
    }

    @Override
    public boolean requiresS3Runtime(ServiceShape service) {
        return service.getTrait(ServiceTrait.class)
                .map(t -> t.getArnNamespace().startsWith("s3"))
                .orElse(false);
    }

    @Override
    public OperationSpec analyzeClientOperation(OperationShape op, Model model, ServiceShape service) {
        HttpSpec http = RestJsonProtocolAnalyzer.buildHttpSpec(op);
        StructureShape input = RestJsonProtocolAnalyzer.inputShape(op, model);
        StructureShape output = RestJsonProtocolAnalyzer.outputShape(op, model);

        List<LabelBinding> labels = RestJsonProtocolAnalyzer.buildLabels(input, model);
        List<QueryBinding> queries = RestJsonProtocolAnalyzer.buildQueries(input);
        List<HeaderBinding> headers = RestJsonProtocolAnalyzer.buildHeaders(input);
        BodySpec body = buildXmlBody(input, labels, queries, headers);

        ErrorSpec errors = RestJsonProtocolAnalyzer.buildErrors(op, model, ErrorCodeStrategy.REST_XML);
        AuthSpec auth = RestJsonProtocolAnalyzer.buildAuth(service);
        PaginationSpec pagination = RestJsonProtocolAnalyzer.buildPagination(op);

        String responsePayloadMember = RestJsonProtocolAnalyzer.buildResponsePayloadMember(output);
        String responseCodeMember    = RestJsonProtocolAnalyzer.buildResponseCodeMember(output);
        List<HeaderBinding> responseHeaders = RestJsonProtocolAnalyzer.buildResponseHeaders(output);

        return new OperationSpec(
                op.getId().getName(),
                service.getId().getName(),
                Role.CLIENT,
                http,
                labels,
                queries,
                headers,
                body,
                errors,
                auth,
                RetrySpec.defaultRetry(),
                pagination,
                RestJsonProtocolAnalyzer.outputTypeName(op, model),
                RestJsonProtocolAnalyzer.inputTypeName(op, model),
                BodyEncoding.XML,
                "application/xml",
                ErrorCodeStrategy.REST_XML,
                null,
                responsePayloadMember,
                responseCodeMember,
                responseHeaders);
    }

    // ── Body ─────────────────────────────────────────────────────────────────

    private static BodySpec buildXmlBody(
            StructureShape input,
            List<LabelBinding> labels,
            List<QueryBinding> queries,
            List<HeaderBinding> headers) {
        Set<String> bound = new LinkedHashSet<>();
        labels.forEach(l -> bound.add(l.smithyMemberName()));
        queries.forEach(q -> bound.add(q.smithyMemberName()));
        headers.forEach(h -> bound.add(h.smithyMemberName()));

        Optional<MemberShape> payloadMember = input.getAllMembers().values().stream()
                .filter(m -> m.hasTrait(HttpPayloadTrait.class))
                .findFirst();

        if (payloadMember.isPresent()) {
            String name = payloadMember.get().getMemberName();
            return new BodySpec(BodyEncoding.XML, List.of(name), name);
        }

        List<String> bodyMembers = new ArrayList<>();
        for (MemberShape member : input.getAllMembers().values()) {
            if (!bound.contains(member.getMemberName())) {
                bodyMembers.add(member.getMemberName());
            }
        }
        BodyEncoding encoding = bodyMembers.isEmpty() ? BodyEncoding.NONE : BodyEncoding.XML;
        return new BodySpec(encoding, bodyMembers, null);
    }
}
