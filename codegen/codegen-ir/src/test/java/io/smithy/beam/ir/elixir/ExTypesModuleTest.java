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
                ExNestedModule.nestedModule(
                    "BasicItem",
                    List.of(),
                    List.of(ExDefstruct.defstruct(List.of(":name", ":count"))),
                    List.of())));

    assertThat(module.asString())
        .contains("defmodule BasicServiceTypes do")
        .contains("# Generated types.")
        .contains("@moduledoc")
        .contains("@type basic_string :: String.t()")
        .contains("defmodule BasicItem do")
        .contains("end");
  }
}
