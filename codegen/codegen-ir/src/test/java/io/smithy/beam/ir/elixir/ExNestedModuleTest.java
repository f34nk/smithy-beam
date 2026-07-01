package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExNestedModuleTest {
  @Test
  void nestedModuleAsString() {
    ExNestedModule nested =
        ExNestedModule.nestedModule(
            "BasicItem",
            List.of(ExModuledoc.moduledoc("Basic item shape.")),
            List.of(
                ExDefstruct.defstruct(List.of(":name", ":count")),
                ExTypeDef.structureType(
                    "t", List.of("name: basic_string() | nil", "count: basic_integer() | nil"))),
            List.of());
    assertThat(nested.asString())
        .contains("defmodule BasicItem do")
        .contains("@moduledoc")
        .contains("defstruct [:name, :count]")
        .contains("@type t :: %__MODULE__{")
        .contains("end");
  }

  @Test
  void defstructLiteralSizeEstimate_sumsFieldAndDefstructLengths() {
    ExNestedModule nested =
        ExNestedModule.nestedModule(
            "BasicItem",
            List.of(),
            List.of(
                ExDefstruct.defstruct(List.of(":name", ":count")),
                ExTypeDef.structureType(
                    "t", List.of("name: basic_string() | nil", "count: basic_integer() | nil"))),
            List.of());

    assertThat(nested.hasDefstruct()).isTrue();
    assertThat(nested.defstructLiteralSizeEstimate()).isEqualTo(65);
  }

  @Test
  void defstructLiteralSizeEstimate_zero_forEnumWithoutDefstruct() {
    ExNestedModule nested =
        ExNestedModule.nestedModule(
            "OrderStatus",
            List.of(),
            List.of(ExTypeDef.alias("t", ":pending stdlib_string()")),
            List.of());

    assertThat(nested.hasDefstruct()).isFalse();
    assertThat(nested.defstructLiteralSizeEstimate()).isZero();
  }

  @Test
  void asTopLevelModuleUsesQualifiedModuleName() {
    ExNestedModule nested =
        ExNestedModule.nestedModule(
            "BasicItem",
            List.of(ExModuledoc.moduledoc("Basic item shape.")),
            List.of(
                ExDefstruct.defstruct(List.of(":name", ":count")),
                ExTypeDef.structureType(
                    "t", List.of("name: basic_string() | nil", "count: basic_integer() | nil"))),
            List.of());

    ExTypesModule topLevel = nested.asTopLevelModule("BasicServiceTypes");
    String out = topLevel.asString();

    assertThat(out)
        .contains("defmodule BasicServiceTypes.BasicItem do")
        .doesNotContain("defmodule BasicItem do")
        .contains("@moduledoc")
        .contains("defstruct [:name, :count]")
        .contains("@type t :: %__MODULE__{")
        .contains("end");
  }

  @Test
  void isEnumModule_true_forEnumWithTypeAliasAndFunctions() {
    ExNestedModule nested =
        ExNestedModule.nestedModule(
            "OrderStatus",
            List.of(),
            List.of(ExTypeDef.alias("t", ":pending | :shipped | {:unknown, String.t()}")),
            List.of(
                ExFunction.defFunction(
                    "from",
                    List.of(
                        ExClause.inlineClause(List.of(ExVarPattern.var("v")), ExVar.var("v"))))));

    assertThat(nested.isEnumModule()).isTrue();
    assertThat(nested.enumModuleSizeEstimate()).isGreaterThan(0);
  }

  @Test
  void isEnumModule_false_forStructureWithDefstruct() {
    ExNestedModule nested =
        ExNestedModule.nestedModule(
            "BasicItem",
            List.of(),
            List.of(
                ExDefstruct.defstruct(List.of(":name")),
                ExTypeDef.structureType("t", List.of("name: String.t() | nil"))),
            List.of());

    assertThat(nested.isEnumModule()).isFalse();
    assertThat(nested.enumModuleSizeEstimate()).isZero();
  }

  @Test
  void isEnumModule_false_forTypeAliasWithoutFunctions() {
    ExNestedModule nested =
        ExNestedModule.nestedModule(
            "OrderStatus",
            List.of(),
            List.of(ExTypeDef.alias("t", ":pending | :shipped")),
            List.of());

    assertThat(nested.isEnumModule()).isFalse();
    assertThat(nested.enumModuleSizeEstimate()).isZero();
  }

  @Test
  void enumModuleSizeEstimate_growsWithTypeAliasAndFunctions() {
    String longBody =
        ":v0 | :v1 | :v2 | :v3 | :v4 | :v5 | :v6 | :v7 | :v8 | :v9 | {:unknown, String.t()}";
    ExNestedModule nested =
        ExNestedModule.nestedModule(
            "LargeStatus",
            List.of(),
            List.of(ExTypeDef.alias("t", longBody)),
            List.of(
                ExFunction.defFunction(
                    "from",
                    List.of(ExClause.inlineClause(List.of(ExVarPattern.var("v")), ExVar.var("v")))),
                ExFunction.defFunction(
                    "to",
                    List.of(
                        ExClause.inlineClause(List.of(ExVarPattern.var("v")), ExVar.var("v"))))));

    assertThat(nested.isEnumModule()).isTrue();
    assertThat(nested.enumModuleSizeEstimate()).isGreaterThan(50);
  }
}
