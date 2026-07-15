package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.Spec;
import io.beam.dsl.elixir.Variable;
import java.util.ArrayList;
import java.util.List;

final class ElixirTypesDsl {

  private ElixirTypesDsl() {}

  static List<String> endpointRuleSetEntries(String ruleSetJson) {
    List<String> lines = new ArrayList<>();
    lines.add("@type endpoint_rule_set :: map()");
    lines.add("@endpoint_rule_set_json ~S\"\"\"");
    lines.addAll(List.of(ruleSetJson.split("\n", -1)));
    lines.add("\"\"\"");
    lines.add("Module.register_attribute(__MODULE__, :endpoint_rule_set, persist: true)");
    lines.add("@endpoint_rule_set Jason.decode!(@endpoint_rule_set_json)");
    return lines;
  }

  static Function endpointRuleSetFunction() {
    return Function.of(
        "endpoint_rule_set",
        false,
        List.of(FunctionHead.of(List.of())),
        Variable.of("@endpoint_rule_set"),
        Spec.of("endpoint_rule_set() :: endpoint_rule_set()"),
        null,
        true);
  }
}
