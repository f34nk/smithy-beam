package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExBlankLine;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExSourceLine;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExTypeDef;
import io.smithy.beam.ir.elixir.ExVar;
import java.util.ArrayList;
import java.util.List;

final class ElixirTypesIr {

  private ElixirTypesIr() {}

  static List<ExModuleEntry> endpointRuleSetEntries(String ruleSetJson) {
    List<ExModuleEntry> entries = new ArrayList<>();
    entries.add(new ExBlankLine());
    entries.add(ExTypeDef.alias("endpoint_rule_set", "map()"));
    entries.add(ExSourceLine.line("@endpoint_rule_set_json ~S\"\"\""));
    for (String line : ruleSetJson.split("\n", -1)) {
      entries.add(ExSourceLine.line(line));
    }
    entries.add(ExSourceLine.line("\"\"\""));
    entries.add(
        ExSourceLine.line(
            "Module.register_attribute(__MODULE__, :endpoint_rule_set, persist: true)"));
    entries.add(ExSourceLine.line("@endpoint_rule_set Jason.decode!(@endpoint_rule_set_json)"));
    return entries;
  }

  static ExFunction endpointRuleSetFunction() {
    return ExFunction.functionWithSpec(
        "def",
        "endpoint_rule_set",
        ExSpec.functionSpec("endpoint_rule_set", "", "endpoint_rule_set()"),
        List.of(ExClause.inlineClause(List.of(), ExVar.var("@endpoint_rule_set"))));
  }
}
