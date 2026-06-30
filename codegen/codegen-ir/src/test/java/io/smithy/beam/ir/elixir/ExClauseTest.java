package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExClauseTest {
  private static ExStruct basicItemStruct() {
    return ExStruct.struct(
        "BasicItem",
        ExMapEntry.entry(
            ExAtom.atom("name"),
            ExCall.call(
                "Map", "get", ExVar.var("map"), ExString.string("name"), ExAtom.atom("nil"))),
        ExMapEntry.entry(
            ExAtom.atom("count"),
            ExCall.call(
                "Map", "get", ExVar.var("map"), ExString.string("count"), ExAtom.atom("nil"))));
  }

  @Test
  void inlineAtomClauseLines() {
    ExClause clause = ExClause.inlineClause(List.of(ExAtomPattern.atom("nil")), ExAtom.atom("nil"));
    assertThat(clause.lines(0, "def", "decode_basic_item", true))
        .containsExactly("def decode_basic_item(:nil), do: :nil");
  }

  @Test
  void multilineStructClauseLines() {
    ExClause clause =
        ExClause.blockClause(
            List.of(ExVarPattern.var("map")),
            List.of(ExGuard.guard("is_map", ExVar.var("map"))),
            basicItemStruct());
    assertThat(clause.lines(0, "def", "decode_basic_item", false))
        .containsExactly(
            "def decode_basic_item(map) when is_map(map) do",
            "  %BasicItem{",
            "    name: Map.get(map, \"name\", :nil),",
            "    count: Map.get(map, \"count\", :nil)",
            "  }",
            "end");
  }

  @Test
  void structPatternClauseStaysSingleLine() {
    ExClause clause =
        ExClause.inlineClause(
            List.of(
                ExStructPattern.struct(
                    "RuntimeTypes.HttpResponse",
                    ExStructFieldPattern.fieldPattern("status", ExIntegerPattern.integer(200)),
                    ExStructFieldPattern.fieldPattern("headers", ExVarPattern.var("headers")),
                    ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body")))),
            ExAtom.atom("ok"));
    assertThat(clause.lines(0, "def", "decode_get_name_response", false))
        .containsExactly(
            "def decode_get_name_response(%RuntimeTypes.HttpResponse{status: 200, headers: headers, body: body}), do: :ok");
  }

  @Test
  void structPatternFunctionHeadBreaksAcrossLines() {
    ExClause clause =
        ExClause.blockClause(
            List.of(
                ExStructPattern.struct(
                    "RuntimeTypes.HttpResponse",
                    ExStructFieldPattern.fieldPattern("status", ExIntegerPattern.integer(200)),
                    ExStructFieldPattern.fieldPattern("headers", ExVarPattern.var("headers")),
                    ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body")))),
            ExAtom.atom("ok"));
    assertThat(clause.lines(1, "def", "decode_get_type_closure_response", false))
        .containsExactly(
            "  def decode_get_type_closure_response(%RuntimeTypes.HttpResponse{",
            "    status: 200,",
            "    headers: headers,",
            "    body: body",
            "  }) do",
            "    :ok",
            "  end");
  }

  @Test
  void multipleGuardsJoinWithAnd() {
    ExClause clause =
        ExClause.blockClause(
            List.of(ExVarPattern.var("left"), ExVarPattern.var("right")),
            List.of(
                ExGuard.guard("is_atom", ExVar.var("left")),
                ExGuard.guard("is_binary", ExVar.var("right"))),
            ExAtom.atom("ok"));
    assertThat(clause.lines(0, "defp", "string_equals?", false))
        .containsExactly(
            "defp string_equals?(left, right) when is_atom(left) and is_binary(right) do",
            "  :ok",
            "end");
  }

  @Test
  void multiArgFunctionHeadStaysOnOneLine() {
    ExClause clause =
        ExClause.blockClause(
            List.of(
                ExStructPattern.struct(
                    "RuntimeTypes.HttpRequest",
                    ExStructFieldPattern.fieldPattern("query", ExVarPattern.var("query")),
                    ExStructFieldPattern.fieldPattern("headers", ExVarPattern.var("headers")),
                    ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body"))),
                ExVarPattern.var("label_map")),
            ExAtom.atom("ok"));
    assertThat(clause.lines(1, "def", "decode_get_type_closure_request", false))
        .containsExactly(
            "  def decode_get_type_closure_request(%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body}, label_map) do",
            "    :ok", "  end");
  }
}
