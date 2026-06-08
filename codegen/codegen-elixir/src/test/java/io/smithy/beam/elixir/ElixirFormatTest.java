package io.smithy.beam.elixir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ElixirFormatTest {

    private static String emit(java.util.function.Consumer<ElixirWriter> action) {
        ElixirWriter writer = new ElixirWriter("test.ex", "Test");
        writer.write("defmodule Test do");
        writer.indent();
        action.accept(writer);
        writer.dedent();
        writer.write("end");
        return writer.toString();
    }

    @Test
    void writeSpecSplitsLongSignatureAtDoubleColon() {
        String out = emit(w -> ElixirFormat.writeSpec(
                w,
                "@spec",
                "get_type_closure(client_config(), BasicServiceTypes.GetTypeClosureInput.t())"
                        + " :: {:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}"));

        assertThat(out).contains("@spec get_type_closure(client_config(), BasicServiceTypes.GetTypeClosureInput.t()) ::");
        assertThat(out).contains("        {:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}");
    }

    @Test
    void writeSpecKeepsShortSignatureOnOneLine() {
        String out = emit(w -> ElixirFormat.writeSpec(
                w, "@spec", "callbacks() :: [{atom(), non_neg_integer()}]"));

        assertThat(out).contains("  @spec callbacks() :: [{atom(), non_neg_integer()}]");
        assertThat(out).doesNotContain("::\n          ");
    }

    @Test
    void beginPipelineBindingFormatsMapAndPipes() {
        String out = emit(w -> {
            ElixirFormat.beginPipelineBinding(w, "query");
            w.write("%{");
            w.indent();
            w.write("\"verbose\" => input.verbose");
            w.dedent();
            w.write("}");
            ElixirFormat.writePipelineStep(w, "Enum.reject(fn {_, v} -> is_nil(v) end)");
            ElixirFormat.writePipelineStep(w, "Map.new()");
            ElixirFormat.endPipelineBinding(w);
        });

        assertThat(out).contains("query =");
        assertThat(out).contains("\"verbose\" => input.verbose");
        assertThat(out).contains("|> Enum.reject(fn {_, v} -> is_nil(v) end)");
        assertThat(out).contains("|> Map.new()");
        assertThat(out).contains("query =\n    %{");
    }

    @Test
    void writeIfInListFormatsConditionalEntry() {
        String out = emit(w -> {
            ElixirFormat.beginPipelineBinding(w, "extra_headers");
            w.write("[");
            w.indent();
            ElixirFormat.writeIfInList(
                    w,
                    "input.request_tag != nil",
                    "{\"X-Request-Tag\", to_string(input.request_tag)}");
            w.dedent();
            w.write("]");
            ElixirFormat.writePipelineStep(w, "Enum.reject(&is_nil/1)");
            ElixirFormat.endPipelineBinding(w);
        });

        assertThat(out).contains("if(input.request_tag != nil,");
        assertThat(out).contains("do: {\"X-Request-Tag\", to_string(input.request_tag)},");
        assertThat(out).contains("else: nil");
    }

    @Test
    void writePipeCaseFieldFormatsHeaderLookup() {
        String out = emit(w -> {
            w.write("%Types.Input{");
            ElixirFormat.writePipeCaseField(
                    w,
                    "request_tag",
                    "List.keyfind(headers, \"X-Request-Tag\", 0)",
                    List.of(new String[] {"{_, v}", "v"}, new String[] {"nil", "nil"}));
            w.write("}");
        });

        assertThat(out).contains("request_tag:");
        assertThat(out).contains("List.keyfind(headers, \"X-Request-Tag\", 0)");
        assertThat(out).contains("|> case do");
        assertThat(out).contains("{_, v} -> v");
        assertThat(out).contains("nil -> nil");
    }

    @Test
    void breakFunctionHeadSplitsLongDef() {
        String out = emit(w -> ElixirFormat.breakFunctionHead(
                w,
                "def",
                "decode_get_type_closure_request",
                List.of(
                        "%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body}",
                        "label_map")));

        assertThat(out).contains("def decode_get_type_closure_request(");
        assertThat(out).contains("%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body},");
        assertThat(out).contains("label_map");
        assertThat(out).contains(") do");
    }

    @Test
    void writeStructureTypeAlignsClosingBrace() {
        String out = emit(w -> ElixirFormat.writeStructureType(
                w,
                List.of(
                        "name: BasicServiceTypes.basic_string()",
                        "count: BasicServiceTypes.basic_integer() | nil")));

        assertThat(out).contains("@type t :: %__MODULE__{");
        assertThat(out).contains("name: BasicServiceTypes.basic_string(),");
        assertThat(out).contains("count: BasicServiceTypes.basic_integer() | nil");
        assertThat(out).contains("    }");
    }
}
