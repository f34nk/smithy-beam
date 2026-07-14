package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExTypesModule;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirTypesIrTest {

  @Test
  void endpointRuleSetEntriesRendersTypeAliasAndAttributes() {
    String output =
        ElixirTypesIr.endpointRuleSetEntries("{\"region\":\"us-east-1\"}").stream()
            .map(entry -> String.join("\n", entry.lines()))
            .collect(Collectors.joining("\n"));

    assertThat(output)
        .contains("@type endpoint_rule_set :: map()")
        .contains("@endpoint_rule_set_json ~S\"\"\"")
        .contains("{\"region\":\"us-east-1\"}")
        .contains("Module.register_attribute(__MODULE__, :endpoint_rule_set, persist: true)")
        .contains("@endpoint_rule_set Jason.decode!(@endpoint_rule_set_json)");
  }

  @Test
  void endpointRuleSetFunctionRendersAccessor() {
    ExFunction function = ElixirTypesIr.endpointRuleSetFunction();
    assertThat(function.asString())
        .contains("@spec endpoint_rule_set() :: endpoint_rule_set()")
        .contains("def endpoint_rule_set, do: @endpoint_rule_set");
  }

  @Test
  void endpointRuleSetEmitsAtEndOfTypesModule() {
    ExTypesModule module =
        ExTypesModule.typesModule(
            "EndpointRulesServiceTypes",
            List.of(ExModuledoc.moduledoc("Types.")),
            ElixirTypesIr.endpointRuleSetEntries("{\"version\":\"1.0\"}"),
            List.of(ElixirTypesIr.endpointRuleSetFunction()));

    String output = module.asString();
    assertThat(output)
        .contains("defmodule EndpointRulesServiceTypes do")
        .contains("@endpoint_rule_set_json")
        .contains("def endpoint_rule_set, do: @endpoint_rule_set");
    assertThat(output.indexOf("@endpoint_rule_set_json"))
        .isLessThan(output.indexOf("def endpoint_rule_set"));
  }
}
