package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExPatternTest {
  @Test
  void atomPatternAsString() {
    assertThat(ExAtomPattern.atom("ok").asString()).isEqualTo(":ok");
  }

  @Test
  void varPatternAsString() {
    assertThat(ExVarPattern.var("map").asString()).isEqualTo("map");
  }

  @Test
  void nilPatternAsString() {
    assertThat(ExNilPattern.nil().asString()).isEqualTo("nil");
  }

  @Test
  void integerPatternAsString() {
    assertThat(ExIntegerPattern.integer(200).asString()).isEqualTo("200");
  }

  @Test
  void mapPatternAsString() {
    assertThat(
            ExMapPattern.map(
                    ExMapFieldPattern.field(ExString.string("key"), ExVarPattern.var("value")))
                .asString())
        .isEqualTo("%{\"key\" => value}");
  }

  @Test
  void structPatternAsString() {
    assertThat(
            ExStructPattern.struct(
                    "RuntimeTypes.HttpRequest",
                    ExStructFieldPattern.fieldPattern("query", ExVarPattern.var("query")),
                    ExStructFieldPattern.fieldPattern("headers", ExVarPattern.var("headers")),
                    ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body")))
                .asString())
        .isEqualTo("%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body}");
  }

  @Test
  void structFunctionHeadPositionalFieldsAsString() {
    assertThat(
            ExStructPattern.structFunctionHead(
                    "Input", "InputRecord", List.of("field_a", "field_b"))
                .asString())
        .isEqualTo("Input = %InputRecord{field_a, field_b}");
  }

  @Test
  void structPatternBreaksFunctionHead() {
    ExStructPattern pattern =
        ExStructPattern.struct(
            "RuntimeTypes.HttpResponse",
            ExStructFieldPattern.fieldPattern("status", ExIntegerPattern.integer(200)),
            ExStructFieldPattern.fieldPattern("headers", ExVarPattern.var("headers")),
            ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body")));
    assertThat(pattern.breaksFunctionHead()).isTrue();
    assertThat(pattern.functionHeadLines(1, "def", "decode_response", null, true))
        .containsExactly(
            "  def decode_response(%RuntimeTypes.HttpResponse{",
            "    status: 200,", "    headers: headers,", "    body: body", "  }) do");
  }

  @Test
  void listPatternAsString() {
    assertThat(ExListPattern.list(ExAtomPattern.atom("ok"), ExVarPattern.var("tail")).asString())
        .isEqualTo("[:ok, tail]");
  }

  @Test
  void consListPatternAsString() {
    assertThat(ExListPattern.cons(ExVarPattern.var("head"), ExVarPattern.var("tail")).asString())
        .isEqualTo("[head | tail]");
  }
}
