package io.smithy.beam.erlang.codegen.codec;

import io.smithy.beam.core.binding.BindingHelper;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.utils.CaseUtils;

/**
 * {@link ErlangCodec} for JSON-based protocols ({@code restJson1},
 * {@code awsJson1_0}, {@code awsJson1_1}).
 *
 * <p>Uses {@code jsx:encode/1} for request serialisation and
 * {@code jsx:decode/1} for response / error deserialisation. When constructed
 * with {@link #AWS_FLAVOR}, the error decode path additionally inspects the
 * {@code __type} field in the response body to identify the error kind (as
 * required by AWS JSON 1.0 / 1.1).
 *
 * <p>Stateless; one instance may be shared across all operations of a service.
 */
public final class JsonCodec implements ErlangCodec {

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
     * @param awsFlavor when {@code true} the error decode path recognises
     *                  the {@code __type} key used by AWS JSON 1.0 / 1.1
     */
    public JsonCodec(boolean awsFlavor) {
        this.awsFlavor = awsFlavor;
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    /**
     * Emits the {@code Body = …} binding that serialises the operation input
     * into a JSON binary using {@code jsx:encode/1}.
     *
     * <p>For REST operations ({@code @http} present) only document-bound
     * members (those not assigned to a label, query string, or header) are
     * placed in the body. For RPC operations (no {@code @http}) all input
     * members go in the body.
     *
     * <p>When there are no body members the result is {@code Body = <<>>}.
     */
    @Override
    public void writeRequestEncode(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        w.addDependency(ErlangDependency.SMITHY_JSON);

        Collection<MemberShape> bodyMembers = bodyMembers(ctx, op);
        if (bodyMembers.isEmpty()) {
            w.write("Body = <<>>,");
            return;
        }

        StructureShape inputShape =
                ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        String inputRecord = CaseUtils.toSnakeCase(inputShape.getId().getName());

        List<String> entries = new ArrayList<>();
        for (MemberShape member : bodyMembers) {
            String wireKey = wireKey(member);
            String fieldName = ctx.symbolProvider().toMemberName(member);
            entries.add(String.format("    <<\"%s\">> => Input#%s.%s", wireKey, inputRecord, fieldName));
        }

        StringJoiner bodyMap = new StringJoiner(",\n", "#{", "}");
        for (String entry : entries) {
            bodyMap.add(entry);
        }
        w.write("Body = jsx:encode($L),", bodyMap.toString());
    }

    /**
     * Emits the {@code case ResponseBody of} block that deserialises a
     * successful response body into the operation's output record using
     * {@code jsx:decode/1}.
     *
     * <p>An empty body ({@code <<>>}) results in a default output record
     * with all fields set to {@code undefined}. Decode errors are wrapped
     * as {@code {error, {json_decode_error, Reason}}}.
     */
    @Override
    public void writeResponseDecode(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        w.addDependency(ErlangDependency.SMITHY_JSON);

        StructureShape outputShape =
                ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        String outputRecord = CaseUtils.toSnakeCase(outputShape.getId().getName());
        List<MemberShape> members = new ArrayList<>(outputShape.getAllMembers().values());

        w.openBlock("case ResponseBody of");
        w.write("<<>> -> {ok, #$L{}};", outputRecord);
        w.write("_ ->");
        w.indent();
        w.openBlock("try jsx:decode(ResponseBody, [return_maps]) of");
        if (members.isEmpty()) {
            w.write("_ -> {ok, #$L{}}", outputRecord);
        } else {
            w.openBlock("Decoded -> {ok, #$L{", outputRecord);
            for (int i = 0; i < members.size(); i++) {
                MemberShape member = members.get(i);
                String wireKey = wireKey(member);
                String fieldName = ctx.symbolProvider().toMemberName(member);
                boolean last = i == members.size() - 1;
                w.write("$L = maps:get(<<\"$L\">>, Decoded, undefined)$L",
                        fieldName, wireKey, last ? "" : ",");
            }
            w.closeBlock("}}");
        }
        w.write("catch");
        w.indent();
        w.write("_:DecodeError -> {error, {json_decode_error, DecodeError}}");
        w.dedent();
        w.closeBlock("end");
        w.dedent();
        w.closeBlock("end;");
    }

    /**
     * Emits the body of the {@code parse_error/2} function.
     *
     * <p>In standard mode ({@code restJson1}) the error is returned as a
     * raw {@code {error, {http_error, StatusCode, Body}}} tuple.
     *
     * <p>In AWS mode ({@code awsJson1_0} / {@code awsJson1_1}) the body is
     * first decoded and the {@code __type} (or {@code code}) field is
     * extracted to identify the error.
     */
    @Override
    public void writeErrorDecode(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        w.addDependency(ErlangDependency.SMITHY_JSON);

        if (awsFlavor) {
            w.openBlock("try jsx:decode(Body, [return_maps]) of");
            w.write("#{<<\"__type\">> := ErrorType} -> {error, {ErrorType, Body}};");
            w.write("#{<<\"code\">> := Code} -> {error, {Code, Body}};");
            w.write("_ -> {error, {http_error, StatusCode, Body}}");
            w.write("catch");
            w.indent();
            w.write("_:_ -> {error, {http_error, StatusCode, Body}}");
            w.dedent();
            w.closeBlock("end");
        } else {
            w.write("{error, {http_error, StatusCode, Body}}");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the input members that should be placed in the request body.
     *
     * <p>For REST operations ({@code @http} present): document-bound members
     * only (label / query / header members are excluded). For RPC operations
     * (no {@code @http}): all input members.
     */
    private static Collection<MemberShape> bodyMembers(ErlangContext ctx, OperationShape op) {
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
    private static String wireKey(MemberShape member) {
        return member.getTrait(JsonNameTrait.class)
                .map(JsonNameTrait::getValue)
                .orElse(member.getMemberName());
    }
}
