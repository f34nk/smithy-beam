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
}
