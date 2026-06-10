package io.smithy.beam.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangFormatTest {

    private static String emit(Consumer<ErlangWriter> action) {
        ErlangWriter writer = new ErlangWriter("test.erl");
        writer.write("-module(test).");
        writer.indent();
        action.accept(writer);
        writer.dedent();
        return writer.toString();
    }

    @Test
    void writeSpecSplitsLongSignatureAtArrow() {
        String out = emit(w -> ErlangFormat.writeSpec(
                w,
                "get_type_closure(client_config(), get_type_closure_input())"
                        + " -> {'ok', get_type_closure_output()} | {'error', term()}"));

        assertThat(out).contains("-spec get_type_closure(client_config(), get_type_closure_input()) ->");
        assertThat(out).contains("    {'ok', get_type_closure_output()} | {'error', term()}");
    }

    @Test
    void writeSpecKeepsShortSignatureOnOneLine() {
        String out = emit(w -> ErlangFormat.writeSpec(w, "callbacks() -> [{atom(), non_neg_integer()}]"));

        assertThat(out).contains("    -spec callbacks() -> [{atom(), non_neg_integer()}]");
        assertThat(out).doesNotContain("->\n        ");
    }

    @Test
    void writeExportBreaksLongExportList() {
        String out = emit(w -> ErlangFormat.writeExport(
                w,
                List.of(
                        "encode_get_type_closure_request/1",
                        "decode_get_type_closure_request/2",
                        "decode_get_type_closure_response/1",
                        "encode_get_type_closure_response/1",
                        "decode_get_type_closure_response_error/3")));

        assertThat(out).contains("-export([");
        assertThat(out).contains("    encode_get_type_closure_request/1,");
        assertThat(out).contains("    decode_get_type_closure_response_error/3");
        assertThat(out).contains("]).");
    }

    @Test
    void writeUnionTypeUsesLeadingPipeOnContinuations() {
        String out = emit(w -> ErlangFormat.writeUnionType(
                w,
                "basic_union",
                List.of(
                        "{text, basic_string()}",
                        "{number, basic_integer()}",
                        "{flag, basic_boolean()}",
                        "{unknown, binary()}")));

        assertThat(out).contains("-type basic_union ::");
        assertThat(out).contains("    {text, basic_string()}");
        assertThat(out).contains("    | {unknown, binary()}.");
        assertThat(out).doesNotContain("{text, basic_string()}  |");
    }

    @Test
    void writeFiltermapFormatsFunAndArgs() {
        String out = emit(w -> {
            ErlangFormat.writeFiltermap(
                    w,
                    List.of(
                            "(V) when V =/= undefined -> {true, {<<\"verbose\">>, encode_query_value(V)}};",
                            "(_) -> false"),
                    "Verbose");
            w.write(".");
        });

        assertThat(out).contains("lists:filtermap(");
        assertThat(out).contains("    fun");
        assertThat(out).contains("        (V) when V =/= undefined -> {true, {<<\"verbose\">>, encode_query_value(V)}};");
        assertThat(out).contains("    end,");
        assertThat(out).contains("    [Verbose]");
    }

    @Test
    void beginBindingFormatsCaseAssignment() {
        String out = emit(w -> {
            ErlangFormat.beginBinding(w, "Decoded");
            w.write("case Body of");
            w.indent();
            w.write("<<>> -> #{};");
            w.dedent();
            w.write("end,");
            ErlangFormat.endBinding(w);
        });

        assertThat(out).contains("Decoded =");
        assertThat(out).contains("    case Body of");
        assertThat(out).contains("    end,");
    }

    @Test
    void breakFunctionHeadSplitsLongDef() {
        String out = emit(w -> ErlangFormat.breakFunctionHead(
                w,
                "decode_get_type_closure_request",
                List.of(
                        "#http_request{query = Query, headers = Headers, body = Body}",
                        "LabelMap")));

        assertThat(out).contains("decode_get_type_closure_request(");
        assertThat(out).contains("    #http_request{query = Query, headers = Headers, body = Body},");
        assertThat(out).contains("    LabelMap");
        assertThat(out).contains(") ->");
    }
}
