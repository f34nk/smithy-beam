package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExModuleTest {
  @Test
  void moduleRendersCallbackSpecsBeforeFunctions() {
    ExCallbackSpec callback =
        ExCallbackSpec.callbackSpec(
            "handle_get_type_closure",
            List.of("term()", "Types.GetTypeClosureInput.t()", "term()"),
            "{:ok, Types.GetTypeClosureOutput.t()} | {:error, term()}");
    ExFunction callbacksFn =
        ExFunction.functionWithSpec(
            "def",
            "callbacks",
            ExSpec.functionSpec("callbacks", "", "[{atom(), non_neg_integer()}]"),
            List.of(
                ExClause.blockClause(
                    List.of(),
                    ExList.list(
                        ExTuple.tuple(
                            ExAtom.atom("handle_get_type_closure"), ExInteger.integer(3))))));

    ExModule module =
        ExModule.module(
            "BasicServiceBehaviour",
            List.of(ExModuledoc.moduledoc("Generated behaviour.")),
            List.of(ExAliasAttr.alias("BasicServiceTypes", "Types")),
            List.of(callback),
            List.of(callbacksFn));

    assertThat(module.asString())
        .contains("alias BasicServiceTypes, as: Types")
        .contains("@callback handle_get_type_closure(")
        .contains("def callbacks do")
        .contains("{:handle_get_type_closure, 3}");
  }

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
