package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.elixir.ExFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirRouterLabelParsingIrTest {
  @Test
  void labelParsingFunctionsMatchGolden() throws IOException {
    List<ExFunction> functions = ElixirRouterIr.labelParsingFunctions();
    assertThat(functions).hasSize(4);
    for (ExFunction fn : functions) {
      ElixirIrTestSupport.assertStructural(fn);
    }
    String combined =
        functions.stream().map(ExFunction::asString).collect(Collectors.joining("\n\n"));
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
