package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExIf;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModuleAssignAttr;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExNestedModule;
import io.smithy.beam.ir.elixir.ExPreambleEntry;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTypeDef;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.traits.EnumValueTrait;

final class ElixirStringBackedEnumIr {
  private ElixirStringBackedEnumIr() {}

  static ExNestedModule build(EnumShape shape, Symbol symbol) {
    String fromFunction = symbol.expectProperty("fromValueFunction", String.class);
    String toFunction = symbol.expectProperty("toValueFunction", String.class);
    String valuesFunction = symbol.expectProperty("valuesFunction", String.class);

    List<String> wireValues = wireValues(shape);
    List<ExPreambleEntry> preamble = enumModuledoc(shape);
    List<ExModuleAssignAttr> attributes = wireAttributes(wireValues);
    List<ExModuleEntry> entries =
        List.of(ExTypeDef.alias("t", "String.t() | {:unknown, String.t()}"));
    List<ExFunction> functions =
        List.of(
            validQuestionFunction(),
            fromStringFunction(fromFunction),
            toStringFunction(toFunction),
            valuesFunction(valuesFunction));
    return ExNestedModule.nestedModule(
        symbol.getName(), preamble, attributes, entries, functions);
  }

  private static List<String> wireValues(EnumShape shape) {
    return shape.members().stream()
        .map(
            m ->
                m.getTrait(EnumValueTrait.class)
                    .flatMap(EnumValueTrait::getStringValue)
                    .orElse(m.getMemberName()))
        .toList();
  }

  private static List<ExPreambleEntry> enumModuledoc(EnumShape shape) {
    List<ExPreambleEntry> preamble = new ArrayList<>();
    BeamDocumentation.forShape(shape)
        .ifPresentOrElse(
            doc -> preamble.add(ExModuledoc.moduledoc(doc)),
            () ->
                preamble.add(
                    ExModuledoc.moduledoc(
                        "String enum. Values are Smithy wire-format strings. "
                            + "Unknown values are represented as {:unknown, String.t()}.")));
    return preamble;
  }

  private static List<ExModuleAssignAttr> wireAttributes(List<String> wireValues) {
    ExList list =
        ExList.list(wireValues.stream().map(ExString::string).toArray(io.smithy.beam.ir.elixir.ExExpr[]::new));
    return List.of(
        ExModuleAssignAttr.assign("wire_values", list),
        ExModuleAssignAttr.assign(
            "wire_set", ExCall.call("MapSet", "new", ExVar.var("@wire_values"))));
  }

  private static ExFunction validQuestionFunction() {
    return ExFunction.functionWithSpec(
        "def",
        "valid?",
        ExSpec.functionSpec("valid?", "String.t()", "boolean()"),
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_binary", ExVar.var("v"))),
                ExCall.call("MapSet", "member?", ExVar.var("@wire_set"), ExVar.var("v")))));
  }

  private static ExFunction fromStringFunction(String name) {
    return ExFunction.functionWithSpec(
        "def",
        name,
        ExSpec.functionSpec(name, "String.t()", "t()"),
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_binary", ExVar.var("v"))),
                ExIf.ifExpr(
                    ExCallLocal.callLocal("valid?", ExVar.var("v")),
                    ExVar.var("v"),
                    ExTuple.tuple(ExAtom.atom("unknown"), ExVar.var("v"))))));
  }

  private static ExFunction toStringFunction(String name) {
    return ExFunction.functionWithSpec(
        "def",
        name,
        ExSpec.functionSpec(name, "t()", "String.t()"),
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("v")),
                List.of(ExGuard.guard("is_binary", ExVar.var("v"))),
                ExVar.var("v")),
            ExClause.inlineClause(
                List.of(
                    ExTuplePattern.tuple(
                        ExAtomPattern.atom("unknown"), ExVarPattern.var("v"))),
                List.of(ExGuard.guard("is_binary", ExVar.var("v"))),
                ExVar.var("v"))));
  }

  private static ExFunction valuesFunction(String name) {
    return ExFunction.functionWithSpec(
        "def",
        name,
        ExSpec.functionSpec(name, "", "[String.t()]"),
        List.of(ExClause.inlineClause(List.of(), ExVar.var("@wire_values"))));
  }
}
