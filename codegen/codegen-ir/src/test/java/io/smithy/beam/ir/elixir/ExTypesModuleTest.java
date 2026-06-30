package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExTypesModuleTest {
  @Test
  void typesModuleAsString() {
    ExTypesModule module =
        ExTypesModule.typesModule(
            "BasicServiceTypes",
            List.of(
                ExComment.comment("Generated types."),
                ExModuledoc.moduledoc("Types for BasicService.")),
            List.of(
                ExTypeDef.alias("basic_string", "String.t()"),
                ExTypeDef.alias("basic_integer", "integer()"),
                ExNestedModule.nestedModule(
                    "BasicItem",
                    List.of(),
                    List.of(),
                    List.of(ExDefstruct.defstruct(List.of(":name", ":count"))),
                    List.of())));

    String out = module.asString();

    assertThat(out)
        .contains("defmodule BasicServiceTypes do")
        .contains("# Generated types.")
        .contains("@moduledoc")
        .contains("@type basic_string :: String.t()")
        .contains("@type basic_integer :: integer()")
        .contains("defmodule BasicItem do")
        .contains("end");
    assertThat(out).doesNotContain("@type basic_string :: String.t()\n\n  @type");
    assertThat(out).contains("@type basic_integer :: integer()\n\n  defmodule BasicItem do");
  }
}
