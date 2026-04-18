package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.HeaderBinding;
import io.smithy.beam.core.ir.LabelBinding;
import io.smithy.beam.core.ir.QueryBinding;
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

/** {@code aws.protocols#restJson1} protocol analyzer. */
public final class RestJsonProtocolAnalyzer extends AbstractHttpProtocolAnalyzer {

    private static final ShapeId PROTOCOL = ShapeId.from("aws.protocols#restJson1");

    public RestJsonProtocolAnalyzer() {}

    @Override
    public ShapeId getProtocol() {
        return PROTOCOL;
    }

    @Override
    public String contentType(ServiceShape service) {
        return "application/json";
    }

    @Override
    public ErrorCodeStrategy errorStrategy(ServiceShape service) {
        return ErrorCodeStrategy.REST_JSON;
    }

    @Override
    protected BodyEncoding responseBodyEncoding() {
        return BodyEncoding.JSON;
    }

    // ── Body (JSON-specific) ──────────────────────────────────────────────────

    @Override
    protected BodySpec buildRequestBody(
            StructureShape input,
            List<LabelBinding> labels,
            List<QueryBinding> queries,
            List<HeaderBinding> headers,
            Model model) {
        return buildBody(input, labels, queries, headers, model);
    }

    /**
     * Builds the JSON request body by collecting all input members that are not already
     * bound to a URI label, query parameter, or request header. Members marked with
     * {@code @httpPayload} are treated as the sole body member.
     */
    static BodySpec buildBody(
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
            return new BodySpec(BodyEncoding.JSON, List.of(name), name);
        }

        List<String> bodyMembers = new ArrayList<>();
        for (MemberShape member : input.getAllMembers().values()) {
            if (!bound.contains(member.getMemberName())) {
                bodyMembers.add(member.getMemberName());
            }
        }
        BodyEncoding encoding = bodyMembers.isEmpty() ? BodyEncoding.NONE : BodyEncoding.JSON;
        return new BodySpec(encoding, bodyMembers, null);
    }
}
