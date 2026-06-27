package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExModuleTest {
  @Test
  void moduleAsString() {
    ExFunction decode =
        ExFunction.functionWithSpec(
            "def",
            "decode_basic_item",
            ExSpec.functionSpec("decode_basic_item", "nil | map()", "nil | BasicItem.t()"),
            List.of(
                ExClause.inlineClause(List.of(ExAtomPattern.atom("nil")), ExAtom.atom("nil"))));

    ExModule module =
        ExModule.module(
            "BasicServiceRestJson1",
            List.of(ExComment.comment("Generated REST JSON codec.")),
            List.of(
                ExAliasAttr.alias("RuntimeTypes", "RuntimeTypes"),
                ExAliasAttr.alias("BasicServiceTypes", "Types")),
            List.of(decode));

    assertThat(module.asString())
        .contains("defmodule BasicServiceRestJson1 do")
        .contains("# Generated REST JSON codec.")
        .contains("alias RuntimeTypes, as: RuntimeTypes")
        .contains("alias BasicServiceTypes, as: Types")
        .contains("def decode_basic_item(:nil), do: :nil")
        .contains("end");
  }
}
