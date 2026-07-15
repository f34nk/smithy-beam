package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.IntegerExpr;
import io.beam.dsl.elixir.IntegerPattern;
import io.beam.dsl.elixir.IsTypeGuard;
import io.beam.dsl.elixir.ListExpr;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.Moduledoc;
import io.beam.dsl.elixir.Spec;
import io.beam.dsl.elixir.StringPattern;
import io.beam.dsl.elixir.StructPattern;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.TypeDef;
import io.beam.dsl.elixir.TypesModule;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamMemberNullability;
import io.smithy.beam.core.BeamNameUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.ErrorTrait;

final class ElixirTypesNestedIr {
  private ElixirTypesNestedIr() {}

  static ElixirTypesEmbeddedNested buildEnumNestedModule(EnumShape shape, Symbol symbol) {
    List<String> atoms = expectStringListProperty(symbol, "enumAtoms");
    String fromFunction = symbol.expectProperty("fromValueFunction", String.class);
    String toFunction = symbol.expectProperty("toValueFunction", String.class);
    String valuesFunction = symbol.expectProperty("valuesFunction", String.class);
    List<Map.Entry<String, String>> members = new ArrayList<>(shape.getEnumValues().entrySet());
    Moduledoc moduledoc = enumModuledoc(shape, true);
    List<String> extraLines = new ArrayList<>();
    List<Function> functions = new ArrayList<>();

    if (atoms.isEmpty()) {
      extraLines.add("@type t :: {:unknown, String.t()}");
      functions.add(
          enumOneLinerFunction(
              fromFunction,
              Spec.of(fromFunction + "(String.t()) :: t()"),
              List.of(VariablePattern.of("v")),
              TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("v")))));
      functions.add(
          enumOneLinerFunction(
              toFunction,
              Spec.of(toFunction + "(t()) :: String.t()"),
              List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("v")))),
              Variable.of("v")));
      functions.add(
          enumOneLinerFunction(
              valuesFunction, Spec.of("values() :: [t()]"), List.of(), ListExpr.of(List.of())));
    } else {
      String atomVariants =
          atoms.stream().map(atom -> ":" + atom).collect(Collectors.joining(" | "));
      extraLines.add("@type t :: " + atomVariants + " | {:unknown, String.t()}");

      Spec fromSpec = Spec.of(fromFunction + "(String.t()) :: t()");
      for (int i = 0; i < members.size(); i++) {
        Map.Entry<String, String> entry = members.get(i);
        functions.add(
            enumOneLinerFunction(
                fromFunction,
                i == 0 ? fromSpec : null,
                List.of(StringPattern.of(entry.getValue())),
                AtomExpr.of(atoms.get(i))));
      }
      functions.add(
          Function.of(
              fromFunction,
              false,
              List.of(
                  FunctionHead.of(
                      List.of(VariablePattern.of("v")), IsTypeGuard.of("is_binary", "v"))),
              ElixirEnumHelperIr.enumStringDecodeFallbackBody(fromFunction),
              null,
              null,
              false));

      Spec toSpec = Spec.of(toFunction + "(t()) :: String.t()");
      for (int i = 0; i < members.size(); i++) {
        Map.Entry<String, String> entry = members.get(i);
        functions.add(
            enumOneLinerFunction(
                toFunction,
                i == 0 ? toSpec : null,
                List.of(AtomPattern.of(atoms.get(i))),
                io.beam.dsl.elixir.StringExpr.of(entry.getValue())));
      }
      functions.add(
          enumOneLinerFunction(
              toFunction,
              null,
              List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("v")))),
              Variable.of("v")));

      functions.add(
          enumOneLinerFunction(
              valuesFunction,
              Spec.of("values() :: [t()]"),
              List.of(),
              ListExpr.of(atoms.stream().<Expression>map(AtomExpr::of).toList())));
    }

    return new ElixirTypesEmbeddedNested(symbol.getName(), moduledoc, extraLines, functions);
  }

  static ElixirTypesEmbeddedNested buildIntEnumNestedModule(IntEnumShape shape, Symbol symbol) {
    List<String> atoms = expectStringListProperty(symbol, "enumAtoms");
    String fromFunction = symbol.expectProperty("fromValueFunction", String.class);
    String toFunction = symbol.expectProperty("toValueFunction", String.class);
    String valuesFunction = symbol.expectProperty("valuesFunction", String.class);
    List<Map.Entry<String, Integer>> members = new ArrayList<>(shape.getEnumValues().entrySet());
    Moduledoc moduledoc = enumModuledoc(shape, false);
    List<String> extraLines = new ArrayList<>();
    List<Function> functions = new ArrayList<>();

    if (atoms.isEmpty()) {
      extraLines.add("@type t :: {:unknown, integer()}");
      functions.add(
          enumOneLinerFunction(
              fromFunction,
              Spec.of(fromFunction + "(integer()) :: t()"),
              List.of(VariablePattern.of("v")),
              TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("v")))));
      functions.add(
          enumOneLinerFunction(
              toFunction,
              Spec.of(toFunction + "(t()) :: integer()"),
              List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("v")))),
              Variable.of("v")));
      functions.add(
          enumOneLinerFunction(
              valuesFunction, Spec.of("values() :: [t()]"), List.of(), ListExpr.of(List.of())));
    } else {
      String atomVariants =
          atoms.stream().map(atom -> ":" + atom).collect(Collectors.joining(" | "));
      extraLines.add("@type t :: " + atomVariants + " | {:unknown, integer()}");

      Spec fromSpec = Spec.of(fromFunction + "(integer()) :: t()");
      for (int i = 0; i < members.size(); i++) {
        Map.Entry<String, Integer> entry = members.get(i);
        functions.add(
            enumOneLinerFunction(
                fromFunction,
                i == 0 ? fromSpec : null,
                List.of(IntegerPattern.of(entry.getValue())),
                AtomExpr.of(atoms.get(i))));
      }
      functions.add(
          enumOneLinerFunction(
              fromFunction,
              null,
              List.of(VariablePattern.of("v")),
              TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("v")))));

      Spec toSpec = Spec.of(toFunction + "(t()) :: integer()");
      for (int i = 0; i < members.size(); i++) {
        Map.Entry<String, Integer> entry = members.get(i);
        functions.add(
            enumOneLinerFunction(
                toFunction,
                i == 0 ? toSpec : null,
                List.of(AtomPattern.of(atoms.get(i))),
                IntegerExpr.of(entry.getValue())));
      }
      functions.add(
          enumOneLinerFunction(
              toFunction,
              null,
              List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("v")))),
              Variable.of("v")));

      functions.add(
          enumOneLinerFunction(
              valuesFunction,
              Spec.of("values() :: [t()]"),
              List.of(),
              ListExpr.of(atoms.stream().<Expression>map(AtomExpr::of).toList())));
    }

    return new ElixirTypesEmbeddedNested(symbol.getName(), moduledoc, extraLines, functions);
  }

  static TypesModule buildStructureNestedModule(
      StructureShape shape,
      Symbol symbol,
      ElixirContext ctx,
      SymbolProvider sp,
      NullableIndex ni,
      List<MemberShape> members) {
    List<String> fieldLines = new ArrayList<>();
    List<String> defstructFields = new ArrayList<>();
    for (MemberShape member : members) {
      Symbol memberSym = sp.toSymbol(member);
      String fieldName =
          memberSym
              .getProperty("fieldName", String.class)
              .orElse(BeamNameUtils.toSnakeCase(member.getMemberName()));
      String typeStr = ElixirDirectedCodegen.renderElixirType(ctx, memberSym);
      if (BeamMemberNullability.isMemberNullable(ni, shape, member)) {
        typeStr = typeStr + " | nil";
      }
      fieldLines.add(fieldName + ": " + typeStr);
      defstructFields.add(fieldName);
    }
    Moduledoc moduledoc =
        BeamDocumentation.elixirStructureModuledoc(shape)
            .map(Moduledoc::of)
            .orElseGet(() -> Moduledoc.of("structure " + shape.getId().getName()));
    TypeDef typeDef = ElixirBeamIrTypes.structureTypeDef("t", fieldLines);
    return ElixirBeamIrTypes.structNested(
        symbol.getName(), moduledoc, typeDef, ElixirBeamIrTypes.defstructFields(defstructFields));
  }

  static ElixirTypesEmbeddedNested buildErrorNestedModule(
      StructureShape shape,
      String modName,
      ErrorTrait errorTrait,
      boolean isRetryable,
      boolean isThrottling,
      SymbolProvider sp) {
    Moduledoc moduledoc =
        BeamDocumentation.forShape(shape)
            .map(Moduledoc::of)
            .orElseGet(
                () ->
                    Moduledoc.of(
                        "Error from "
                            + shape.getId()
                            + " (fault: "
                            + errorTrait.getValue()
                            + ", retryable: "
                            + isRetryable
                            + ")."));

    List<String> exceptionFields = new ArrayList<>();
    for (MemberShape member : shape.members()) {
      Symbol memberSym = sp.toSymbol(member);
      String fieldName =
          memberSym
              .getProperty("fieldName", String.class)
              .orElse(BeamNameUtils.toSnakeCase(member.getMemberName()));
      exceptionFields.add(fieldName + ": nil");
    }
    exceptionFields.add("__beam_error_kind: :" + errorTrait.getValue());

    List<String> extraLines = new ArrayList<>(ElixirBeamIrTypes.defexceptionLines(exceptionFields));
    List<Function> functions =
        List.of(
            enumOneLinerFunction(
                "retryable",
                null,
                List.of(StructPattern.of("__MODULE__", List.of())),
                AtomExpr.of(isRetryable ? "true" : "false")),
            enumOneLinerFunction(
                "throttling",
                null,
                List.of(StructPattern.of("__MODULE__", List.of())),
                AtomExpr.of(isThrottling ? "true" : "false")),
            enumOneLinerFunction(
                "message",
                null,
                List.of(VariablePattern.of("e")),
                LocalCallExpr.of("inspect", List.of(Variable.of("e")))));

    return new ElixirTypesEmbeddedNested(modName, moduledoc, extraLines, functions);
  }

  private static Function enumOneLinerFunction(
      String name,
      Spec specOrNull,
      List<io.beam.dsl.elixir.Pattern> params,
      io.beam.dsl.elixir.Expression body) {
    return Function.of(name, false, List.of(FunctionHead.of(params)), body, specOrNull, null, true);
  }

  private static Moduledoc enumModuledoc(Shape shape, boolean stringEnum) {
    return BeamDocumentation.forShape(shape)
        .map(Moduledoc::of)
        .orElseGet(
            () ->
                Moduledoc.of(
                    stringEnum
                        ? "String enum. Unknown values are represented as {:unknown, String.t()}."
                        : "Integer enum. Unknown values are represented as {:unknown, integer()}."));
  }

  private static List<String> expectStringListProperty(Symbol symbol, String propertyName) {
    List<?> values = symbol.expectProperty(propertyName, List.class);
    return values.stream().map(String.class::cast).toList();
  }
}
