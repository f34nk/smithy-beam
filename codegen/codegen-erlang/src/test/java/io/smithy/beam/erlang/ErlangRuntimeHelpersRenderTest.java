package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ErlangRuntimeHelpersRenderTest {

  @Test
  void labelParsingUsesMapUpdateAndSplitTailPattern() {
    String rendered =
        ErlangRuntimeHelpersIr.labelParsingFunctions().stream()
            .map(ErlangRenderer::renderFunction)
            .collect(Collectors.joining("\n\n"));
    assertThat(rendered).contains("Acc#{Key => Val}");
    assertThat(rendered).contains("[Label | [<<>>]]");
  }
}
