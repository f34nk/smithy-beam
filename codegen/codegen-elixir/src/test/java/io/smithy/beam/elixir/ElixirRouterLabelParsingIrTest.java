package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Function;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirRouterLabelParsingIrTest {
  @Test
  void labelParsingFunctionsMatchGolden() throws IOException {
    List<Function> functions = ElixirRouterIr.labelParsingFunctions();
    assertThat(functions).hasSize(7);
    for (Function fn : functions) {
      ElixirIrTestSupport.assertStructural(fn);
    }
    String combined =
        functions.stream().map(ElixirRenderer::renderFunction).collect(Collectors.joining("\n\n"));
    assertThat(combined)
        .isEqualTo(readExpectedString("ir/runtime_helpers_label_parsing.expected.ex"));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirRouterLabelParsingIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
