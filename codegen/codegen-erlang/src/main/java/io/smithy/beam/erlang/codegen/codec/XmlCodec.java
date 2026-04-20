package io.smithy.beam.erlang.codegen.codec;

import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangDependency;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.utils.CaseUtils;

/**
 * {@link ErlangCodec} for the {@code restXml} protocol.
 *
 * <p>Uses {@code smithy_xml:encode/1} for request serialisation and
 * {@code smithy_xml:decode/2} for response / error deserialisation.
 *
 * <p>Stateless; one instance may be shared across all operations of a service.
 */
public final class XmlCodec implements ErlangCodec {

    @Override
    public String contentType() {
        return "application/xml";
    }

    /**
     * Emits {@code Body = smithy_xml:encode(Input)} for the request body.
     *
     * <p>The runtime {@code smithy_xml} module is responsible for traversing
     * the record and producing the XML document. An empty record (all fields
     * {@code undefined}) results in an empty XML document.
     */
    @Override
    public void writeRequestEncode(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        w.addDependency(ErlangDependency.SMITHY_XML);

        StructureShape inputShape =
                ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        String inputRecord = CaseUtils.toSnakeCase(inputShape.getId().getName());

        if (inputShape.getAllMembers().isEmpty()) {
            w.write("Body = <<>>,");
        } else {
            w.write("Body = smithy_xml:encode(Input, $S),", inputRecord);
        }
    }

    /**
     * Emits the {@code case ResponseBody of} block that deserialises a
     * successful XML response body into the operation's output record using
     * {@code smithy_xml:decode/2}.
     */
    @Override
    public void writeResponseDecode(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        w.addDependency(ErlangDependency.SMITHY_XML);

        StructureShape outputShape =
                ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        String outputRecord = CaseUtils.toSnakeCase(outputShape.getId().getName());

        w.openBlock("case ResponseBody of");
        w.write("<<>> -> {ok, #$L{}};", outputRecord);
        w.write("_ ->");
        w.indent();
        w.openBlock("try smithy_xml:decode(ResponseBody, $S) of", outputRecord);
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
     * Emits the body of the {@code parse_error/2} function for XML error
     * responses, delegating to {@code smithy_xml:decode_error/2}.
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
        w.closeBlock("end.");
    }
}
