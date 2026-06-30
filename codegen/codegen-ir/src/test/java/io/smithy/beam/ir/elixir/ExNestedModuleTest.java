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
                    "t",
                    List.of(
                        "name: basic_string() | nil",
                        "count: basic_integer() | nil"))),
            List.of());
    assertThat(nested.asString())
        .contains("defmodule BasicItem do")
        .contains("@moduledoc")
        .contains("defstruct [:name, :count]")
        .contains("@type t :: %__MODULE__{")
        .contains("end");
  }
}
