package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.erlang.Function;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErlangRuntimeHelpersRenderTest {

  @Test
  void labelParsingUsesMapUpdateAndSplitTailPattern() {
    String rendered = DslGoldenAssertions.renderFunctions(ErlangRouterDsl.labelParsingFunctions());
    assertThat(rendered).contains("Acc#{Key => Val}");
    assertThat(rendered).contains("[Label | [<<>>]]");
    List<Function> functions = ErlangRouterDsl.labelParsingFunctions();
    assertThat(functions).hasSize(4);
  }
}
