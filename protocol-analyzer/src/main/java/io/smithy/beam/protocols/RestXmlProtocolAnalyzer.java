package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.HeaderBinding;
import io.smithy.beam.core.ir.LabelBinding;
import io.smithy.beam.core.ir.QueryBinding;
import software.amazon.smithy.aws.traits.ServiceTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
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
 * as XML rather than JSON.  Requires the {@code smithy_xml.erl} runtime module.
 *
 * <p>For S3-like services (where the {@code aws.api#service} trait's {@code arnNamespace} starts
 * with {@code "s3"}), {@link #requiresS3Runtime} returns {@code true} so that the pipeline also
 * copies {@code smithy_s3.erl} into the build output.
 */
public final class RestXmlProtocolAnalyzer extends AbstractHttpProtocolAnalyzer {

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
    public ErrorCodeStrategy errorStrategy(ServiceShape service) {
        return ErrorCodeStrategy.REST_XML;
    }

    @Override
    protected BodyEncoding responseBodyEncoding() {
        return BodyEncoding.XML;
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

    // ── Body (XML-specific) ───────────────────────────────────────────────────

    @Override
    protected BodySpec buildRequestBody(
            StructureShape input,
            List<LabelBinding> labels,
            List<QueryBinding> queries,
            List<HeaderBinding> headers,
            Model model) {
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
