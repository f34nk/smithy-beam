package io.smithy.beam.elixir.codegen.codec;

import io.smithy.beam.core.binding.BindingHelper;
import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirDependency;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.JsonNameTrait;

/**
 * {@link ElixirCodec} for JSON-based protocols ({@code restJson1},
 * {@code awsJson1_0}, {@code awsJson1_1}).
 *
 * <p>Emits declarative fields into the {@code %SmithyClient.Operation{}} struct
 * that drive runtime encode / decode in the {@code SmithyClient} and
 * {@code SmithyJson} modules. When constructed with {@link #AWS_FLAVOR} the
 * {@code parse_error_fn} pointer targets the AWS-JSON error resolver that
 * uses the {@code __type} field.
 *
 * <p>Stateless; one instance may be shared across all operations of a service.
 */
public final class JsonCodec implements ElixirCodec {

    /** Sentinel constant for the AWS-JSON error-type-aware variant. */
    public static final boolean AWS_FLAVOR = true;

    private final boolean awsFlavor;

    /** Constructs a standard JSON codec (restJson1 style). */
    public JsonCodec() {
        this(false);
    }

    /**
     * Constructs a JSON codec.
     *
     * @param awsFlavor when {@code true} the emitted {@code parse_error_fn}
     *                  targets an AWS-JSON-aware resolver that inspects the
     *                  {@code __type} field
     */
    public JsonCodec(boolean awsFlavor) {
        this.awsFlavor = awsFlavor;
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    /**
     * Emits the {@code content_type:} and {@code encoding:} fields for the
     * {@code %SmithyClient.Operation{}} struct.
     *
     * <p>For REST operations ({@code @http} present) with no document-bound
     * members (e.g. GET with only path / query / header members) the encoding
     * is {@code :none}. Otherwise the encoding is {@code :json}.
     */
    @Override
    public void writeRequestEncode(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_JSON);

        Collection<MemberShape> bodyMembers = bodyMembers(ctx, op);
        String encoding = bodyMembers.isEmpty() ? ":none" : ":json";

        w.write("content_type: $S,", contentType());
        w.write("encoding: $L,", encoding);
    }

    /**
     * Emits the {@code decoding:} field for the
     * {@code %SmithyClient.Operation{}} struct.
     */
    @Override
    public void writeResponseDecode(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_JSON);

        w.write("decoding: :json,");
    }

    /**
     * Emits the {@code parse_error_fn:} field for the
     * {@code %SmithyClient.Operation{}} struct.
     *
     * <p>In standard mode the local {@code parse_error/2} function is used.
     * In AWS mode a dedicated resolver that extracts {@code __type} from the
     * JSON body is referenced instead.
     */
    @Override
    public void writeErrorDecode(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_JSON);

        if (awsFlavor) {
            w.write("parse_error_fn: &SmithyJson.parse_aws_error/2,");
        } else {
            w.write("parse_error_fn: &parse_error/2,");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the input members that should be placed in the request body.
     *
     * <p>For REST operations ({@code @http} present): document-bound members
     * only. For RPC operations (no {@code @http}): all input members.
     */
    private static Collection<MemberShape> bodyMembers(ElixirContext ctx, OperationShape op) {
        if (BindingHelper.http(op).isPresent()) {
            List<MemberShape> result = new ArrayList<>();
            for (HttpBinding b : BindingHelper.documentMembers(ctx.model(), op).values()) {
                result.add(b.getMember());
            }
            return result;
        }
        return ctx.model()
                .expectShape(op.getInputShape(), StructureShape.class)
                .getAllMembers()
                .values();
    }

    /** Returns the JSON wire key for a member: {@code @jsonName} if present, else the member name. */
    @SuppressWarnings("unused")
    private static String wireKey(MemberShape member) {
        return member.getTrait(JsonNameTrait.class)
                .map(JsonNameTrait::getValue)
                .orElse(member.getMemberName());
    }
}
