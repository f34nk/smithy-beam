package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Header;
import org.junit.jupiter.api.Test;

class ErlangTypesIrTest {

  @Test
  void endpointRuleSetEntriesRendersTypeAliasAndDefine() {
    String map = "#{'argv' => [<<\"us-east-1\">>]}";
    String output =
        ErlangRenderer.render(Header.ofEntries(ErlangTypesIr.endpointRuleSetEntries(map), false));
    assertThat(output)
        .contains("%% @endpointRuleSet embedded at codegen time.")
        .contains("-type endpoint_rule_set() :: map().")
        .contains("-define(ENDPOINT_RULE_SET, " + map + ").");
  }
}
