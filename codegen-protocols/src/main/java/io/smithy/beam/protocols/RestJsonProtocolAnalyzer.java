package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.AuthSpec;
import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.ErrorBinding;
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
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.PaginatedTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** {@code aws.protocols#restJson1} protocol analyzer. */
public final class RestJsonProtocolAnalyzer implements ProtocolAnalyzer {

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
    public OperationSpec analyzeClientOperation(OperationShape op, Model model, ServiceShape service) {
        HttpSpec http = buildHttpSpec(op);
        StructureShape input = inputShape(op, model);

        List<LabelBinding> labels = buildLabels(input, model);
        List<QueryBinding> queries = buildQueries(input);
        List<HeaderBinding> headers = buildHeaders(input);
        BodySpec body = buildBody(input, labels, queries, headers, model);
        ErrorSpec errors = buildErrors(op, model, ErrorCodeStrategy.REST_JSON);
        AuthSpec auth = buildAuth(service);
        PaginationSpec pagination = buildPagination(op);

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
                outputTypeName(op, model),
                inputTypeName(op, model),
                BodyEncoding.JSON,
                "application/json",
                ErrorCodeStrategy.REST_JSON);
    }

    @Override
    public OperationSpec analyzeServerOperation(OperationShape op, Model model, ServiceShape service) {
        HttpSpec http = buildHttpSpec(op);
        StructureShape input = inputShape(op, model);

        List<LabelBinding> labels = buildLabels(input, model);
        List<QueryBinding> queries = buildQueries(input);
        List<HeaderBinding> headers = buildHeaders(input);
        BodySpec body = buildBody(input, labels, queries, headers, model);
        ErrorSpec errors = buildErrors(op, model, ErrorCodeStrategy.REST_JSON);

        return new OperationSpec(
                op.getId().getName(),
                service.getId().getName(),
                Role.SERVER,
                http,
                labels,
                queries,
                headers,
                body,
                errors,
                AuthSpec.none(),
                RetrySpec.disabled(),
                null,
                outputTypeName(op, model),
                inputTypeName(op, model),
                BodyEncoding.JSON,
                "application/json",
                ErrorCodeStrategy.REST_JSON);
    }

    // ── HttpSpec ─────────────────────────────────────────────────────────────

    static HttpSpec buildHttpSpec(OperationShape op) {
        return op.getTrait(HttpTrait.class)
                .map(h -> new HttpSpec(h.getMethod(), h.getUri().toString(), h.getCode()))
                .orElse(new HttpSpec("POST", "/", 200));
    }

    // ── Input shape ──────────────────────────────────────────────────────────

    static StructureShape inputShape(OperationShape op, Model model) {
        return model.expectShape(op.getInputShape(), StructureShape.class);
    }

    // ── Labels ───────────────────────────────────────────────────────────────

    static List<LabelBinding> buildLabels(StructureShape input, Model model) {
        List<LabelBinding> result = new ArrayList<>();
        for (MemberShape member : input.getAllMembers().values()) {
            if (member.hasTrait(HttpLabelTrait.class)) {
                Shape target = model.expectShape(member.getTarget());
                boolean requiresEncoding = target.getType().toString().equals("string") && !target.isEnumShape();
                result.add(new LabelBinding(member.getMemberName(), member.getMemberName(), requiresEncoding));
            }
        }
        return result;
    }

    // ── Query parameters ─────────────────────────────────────────────────────

    static List<QueryBinding> buildQueries(StructureShape input) {
        List<QueryBinding> result = new ArrayList<>();
        for (MemberShape member : input.getAllMembers().values()) {
            member.getTrait(HttpQueryTrait.class).ifPresent(t ->
                    result.add(new QueryBinding(member.getMemberName(), t.getValue())));
        }
        return result;
    }

    // ── Headers ──────────────────────────────────────────────────────────────

    static List<HeaderBinding> buildHeaders(StructureShape input) {
        List<HeaderBinding> result = new ArrayList<>();
        for (MemberShape member : input.getAllMembers().values()) {
            member.getTrait(HttpHeaderTrait.class).ifPresent(t ->
                    result.add(new HeaderBinding(
                            member.getMemberName(),
                            t.getValue(),
                            member.hasTrait(RequiredTrait.class))));
        }
        return result;
    }

    // ── Body ─────────────────────────────────────────────────────────────────

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

    // ── Errors ───────────────────────────────────────────────────────────────

    static ErrorSpec buildErrors(OperationShape op, Model model, ErrorCodeStrategy strategy) {
        List<ErrorBinding> result = new ArrayList<>();
        for (ShapeId errorId : op.getErrors()) {
            Shape errorShape = model.expectShape(errorId);
            int code = errorShape.getTrait(HttpErrorTrait.class)
                    .map(HttpErrorTrait::getCode)
                    .orElse(400);
            result.add(new ErrorBinding(errorId.getName(), code, strategy));
        }
        return new ErrorSpec(result, strategy);
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    static AuthSpec buildAuth(ServiceShape service) {
        return service.getTrait(SigV4Trait.class)
                .map(t -> new AuthSpec(true, t.getName()))
                .orElse(AuthSpec.none());
    }

    // ── Output type name ─────────────────────────────────────────────────────

    /**
     * Returns the actual Smithy output shape name for the operation.
     * Returns {@code "map"} for unit outputs (no meaningful output type).
     */
    static String outputTypeName(OperationShape op, Model model) {
        ShapeId outputId = op.getOutputShape();
        if ("smithy.api".equals(outputId.getNamespace()) && "Unit".equals(outputId.getName())) {
            return "map";
        }
        return model.expectShape(outputId).getId().getName();
    }

    /**
     * Returns the actual Smithy input shape name for the operation.
     * Returns {@code "map"} for unit inputs.
     */
    static String inputTypeName(OperationShape op, Model model) {
        ShapeId inputId = op.getInputShape();
        if ("smithy.api".equals(inputId.getNamespace()) && "Unit".equals(inputId.getName())) {
            return "map";
        }
        return model.expectShape(inputId).getId().getName();
    }

    // ── Pagination ───────────────────────────────────────────────────────────

    static PaginationSpec buildPagination(OperationShape op) {
        return op.getTrait(PaginatedTrait.class)
                .map(p -> new PaginationSpec(
                        p.getInputToken().orElse(null),
                        p.getOutputToken().orElse(null),
                        p.getItems().orElse(null),
                        p.getPageSize().orElse(null)))
                .orElse(null);
    }
}
