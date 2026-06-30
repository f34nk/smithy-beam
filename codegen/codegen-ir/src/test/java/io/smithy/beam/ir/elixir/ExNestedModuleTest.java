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
            List.of(),
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
  void nestedModuleRendersAttributesInsideDefmodule() {
    ExNestedModule nested =
        ExNestedModule.nestedModule(
            "WireEnum",
            List.of(ExModuledoc.moduledoc("Wire enum.")),
            List.of(
                ExModuleAssignAttr.assign(
                    "wire_values", ExList.list(ExString.string("ALPHA"), ExString.string("BETA"))),
                ExModuleAssignAttr.assign(
                    "wire_set", ExCall.call("MapSet", "new", ExVar.var("wire_values")))),
            List.of(ExTypeDef.alias("t", "String.t()")),
            List.of());
    assertThat(nested.asString())
        .contains("defmodule WireEnum do")
        .contains("@wire_values [\"ALPHA\", \"BETA\"]")
        .contains("@wire_set MapSet.new(wire_values)")
        .contains("@type t :: String.t()")
        .contains("end");
  }
}
