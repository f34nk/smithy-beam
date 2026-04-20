package io.smithy.beam.erlang.codegen.codec;

import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.utils.CaseUtils;

/**
 * {@link ErlangCodec} for the {@code awsQuery} protocol.
 *
 * <p>Uses {@code smithy_query:encode/2} for request serialisation
 * (URL-form-encoded body) and {@code smithy_xml:decode/2} for response /
 * error deserialisation (Query protocol responses are XML-wrapped).
 *
 * <p>Stateless; one instance may be shared across all operations of a service.
 */
public final class QueryCodec implements ErlangCodec {

    @Override
    public String contentType() {
        return "application/x-www-form-urlencoded";
    }

    /**
     * Emits {@code Body = smithy_query:encode(Input, Action)} where
     * {@code Action} is the Smithy operation name. The runtime
     * {@code smithy_query} module converts the record to URL-form-encoded
     * key-value pairs prefixed with {@code Action=…&Version=…}.
     */
    @Override
    public void writeRequestEncode(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        w.addDependency(ErlangDependency.SMITHY_QUERY);

        StructureShape inputShape =
                ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        String inputRecord = CaseUtils.toSnakeCase(inputShape.getId().getName());
        String action = op.getId().getName();

        if (inputShape.getAllMembers().isEmpty()) {
            w.write("Body = smithy_query:encode_action($S),", action);
        } else {
            w.write("Body = smithy_query:encode(Input, $S, $S),", inputRecord, action);
        }
    }

    /**
     * Emits the {@code case ResponseBody of} block that deserialises a
     * successful Query-protocol XML response body using
     * {@code smithy_xml:decode/2}.
     */
    @Override
    public void writeResponseDecode(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        w.addDependency(ErlangDependency.SMITHY_QUERY);
        w.addDependency(ErlangDependency.SMITHY_XML);

        StructureShape outputShape =
                ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        String outputRecord = CaseUtils.toSnakeCase(outputShape.getId().getName());
        String resultWrapper = op.getId().getName() + "Result";

        w.openBlock("case ResponseBody of");
        w.write("<<>> -> {ok, #$L{}};", outputRecord);
        w.write("_ ->");
        w.indent();
        w.openBlock("try smithy_xml:decode(ResponseBody, $S, $S) of", outputRecord, resultWrapper);
        w.write("Decoded -> {ok, Decoded}");
        w.write("catch");
        w.indent();
        w.write("_:DecodeError -> {error, {xml_decode_error, DecodeError}}");
        w.dedent();
        w.closeBlock("end");
        w.dedent();
        w.closeBlock("end;");
    }

    /**
     * Emits the body of the {@code parse_error/2} function, delegating to
     * {@code smithy_xml:decode_error/2} for the XML error response body.
     */
    @Override
    public void writeErrorDecode(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        w.addDependency(ErlangDependency.SMITHY_XML);

        w.openBlock("try smithy_xml:decode_error(StatusCode, Body) of");
        w.write("{ErrorCode, Message} -> {error, {ErrorCode, Message, StatusCode}}");
        w.write("catch");
        w.indent();
        w.write("_:_ -> {error, {http_error, StatusCode, Body}}");
        w.dedent();
        w.closeBlock("end");
    }
}
