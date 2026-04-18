package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.AuthSpec;
import io.smithy.beam.core.ir.ErrorBinding;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.ErrorSpec;
import io.smithy.beam.core.ir.HeaderBinding;
import io.smithy.beam.core.ir.HttpSpec;
import io.smithy.beam.core.ir.LabelBinding;
import io.smithy.beam.core.ir.PaginationSpec;
import io.smithy.beam.core.ir.QueryBinding;
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
import software.amazon.smithy.model.traits.HttpResponseCodeTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.PaginatedTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless utility that extracts HTTP binding information from Smithy model shapes into
 * protocol-agnostic IR objects.
 *
 * <p>All methods are static. This class cannot be instantiated. Protocol analyzers delegate to
 * these helpers so that the extraction logic lives in one place rather than being duplicated or
 * coupled through {@link RestJsonProtocolAnalyzer} inheritance.
 */
final class HttpBindingExtractor {

    private HttpBindingExtractor() {}

    // ── HttpSpec ─────────────────────────────────────────────────────────────

    static HttpSpec buildHttpSpec(OperationShape op) {
        return op.getTrait(HttpTrait.class)
                .map(h -> new HttpSpec(h.getMethod(), h.getUri().toString(), h.getCode()))
                .orElse(new HttpSpec("POST", "/", 200));
    }

    // ── Input / output shapes ─────────────────────────────────────────────────

    static StructureShape inputShape(OperationShape op, Model model) {
        return model.expectShape(op.getInputShape(), StructureShape.class);
    }

    static StructureShape outputShape(OperationShape op, Model model) {
        return model.expectShape(op.getOutputShape(), StructureShape.class);
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

    // ── Request headers ──────────────────────────────────────────────────────

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

    // ── Response bindings (output shape) ─────────────────────────────────────

    /**
     * Returns the Smithy member name of the {@code @httpPayload} member in the output shape,
     * or {@code null} if there is no such member.
     */
    static String buildResponsePayloadMember(StructureShape output) {
        return output.getAllMembers().values().stream()
                .filter(m -> m.hasTrait(HttpPayloadTrait.class))
                .map(MemberShape::getMemberName)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the Smithy member name of the {@code @httpResponseCode} member in the output
     * shape, or {@code null} if there is no such member.
     */
    static String buildResponseCodeMember(StructureShape output) {
        return output.getAllMembers().values().stream()
                .filter(m -> m.hasTrait(HttpResponseCodeTrait.class))
                .map(MemberShape::getMemberName)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns {@link HeaderBinding} entries for every {@code @httpHeader}-annotated member in
     * the output shape. These represent response headers that the client should extract and
     * place into the response map.
     */
    static List<HeaderBinding> buildResponseHeaders(StructureShape output) {
        List<HeaderBinding> result = new ArrayList<>();
        for (MemberShape member : output.getAllMembers().values()) {
            member.getTrait(HttpHeaderTrait.class).ifPresent(t ->
                    result.add(new HeaderBinding(
                            member.getMemberName(),
                            t.getValue(),
                            member.hasTrait(RequiredTrait.class))));
        }
        return result;
    }

    // ── Errors ───────────────────────────────────────────────────────────────

    /**
     * Builds the {@link ErrorSpec} for an operation.
     *
     * @param messageMemberName body field name for the human-readable error message
     *                          (e.g. {@code "Message"} for AWS protocols; {@code null} for non-AWS)
     */
    static ErrorSpec buildErrors(OperationShape op, Model model, ErrorCodeStrategy strategy,
                                 String messageMemberName) {
        List<ErrorBinding> result = new ArrayList<>();
        for (ShapeId errorId : op.getErrors()) {
            Shape errorShape = model.expectShape(errorId);
            int code = errorShape.getTrait(HttpErrorTrait.class)
                    .map(HttpErrorTrait::getCode)
                    .orElse(400);
            result.add(new ErrorBinding(errorId.getName(), code, strategy, messageMemberName));
        }
        return new ErrorSpec(result, strategy);
    }

    /**
     * Convenience overload for AWS-flavoured protocols where the message field is always
     * {@code "Message"}.
     */
    static ErrorSpec buildErrors(OperationShape op, Model model, ErrorCodeStrategy strategy) {
        return buildErrors(op, model, strategy, "Message");
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    static AuthSpec buildAuth(ServiceShape service) {
        return service.getTrait(SigV4Trait.class)
                .map(t -> new AuthSpec(true, t.getName()))
                .orElse(AuthSpec.none());
    }

    // ── Type names ───────────────────────────────────────────────────────────

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
