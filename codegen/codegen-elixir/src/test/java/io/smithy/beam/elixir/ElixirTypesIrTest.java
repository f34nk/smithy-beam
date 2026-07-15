package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.Module;
import io.beam.ir.elixir.Moduledoc;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirTypesIrTest {

  @Test
  void endpointRuleSetEntriesRendersTypeAliasAndAttributes() {
    String output =
        String.join("\n", ElixirTypesIr.endpointRuleSetEntries("{\"region\":\"us-east-1\"}"));

    assertThat(output)
        .contains("@type endpoint_rule_set :: map()")
        .contains("@endpoint_rule_set_json ~S\"\"\"")
        .contains("{\"region\":\"us-east-1\"}")
        .contains("Module.register_attribute(__MODULE__, :endpoint_rule_set, persist: true)")
        .contains("@endpoint_rule_set Jason.decode!(@endpoint_rule_set_json)");
  }

  @Test
  void endpointRuleSetFunctionRendersAccessor() {
    Function function = ElixirTypesIr.endpointRuleSetFunction();
    assertThat(ElixirRenderer.renderFunction(function))
        .contains("@spec endpoint_rule_set() :: endpoint_rule_set()")
        .contains("def endpoint_rule_set, do: @endpoint_rule_set");
  }

  @Test
  void endpointRuleSetEmitsAtEndOfTypesModule() {
    List<ElixirTypesEntry> entries =
        ElixirTypesIr.endpointRuleSetEntries("{\"version\":\"1.0\"}").stream()
            .map(ElixirTypesRootLine::new)
            .map(ElixirTypesEntry.class::cast)
            .toList();
    Module module =
        ElixirBeamIrTypes.rootTypesModule(
            "EndpointRulesServiceTypes",
            Moduledoc.of("Types."),
            entries,
            List.of(ElixirTypesIr.endpointRuleSetFunction()));

    String output = ElixirRenderer.render(module);
    assertThat(output)
        .contains("defmodule EndpointRulesServiceTypes do")
        .contains("@endpoint_rule_set_json")
        .contains("def endpoint_rule_set, do: @endpoint_rule_set");
    assertThat(output.indexOf("@endpoint_rule_set_json"))
        .isLessThan(output.indexOf("def endpoint_rule_set"));
  }
}
