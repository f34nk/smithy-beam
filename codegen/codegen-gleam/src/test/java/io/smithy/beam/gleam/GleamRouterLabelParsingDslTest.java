package io.smithy.beam.gleam;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.lang.gleam.Function;
import io.beam.lang.gleam.GleamRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GleamRouterLabelParsingDslTest {

  @Test
  void labelParsingFunctionsMatchGolden() throws IOException {
    List<Function> functions = GleamRouterDsl.labelParsingFunctions();
    assertThat(functions).hasSize(4);
    String combined =
        functions.stream().map(GleamRenderer::render).collect(Collectors.joining("\n"));
    assertThat(combined.stripTrailing()).isEqualTo(readExpected("dsl/router_label_parsing.expected.gleam"));
  }

  private static String readExpected(String resourcePath) throws IOException {
    try (InputStream in =
        GleamRouterLabelParsingDslTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return text.stripTrailing();
    }
  }
}
