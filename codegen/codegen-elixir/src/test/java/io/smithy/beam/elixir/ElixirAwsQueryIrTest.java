package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.elixir.ExFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ElixirAwsQueryIrTest {
  @Test
  void queryHelpersAwsAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirAwsQueryHelperIr.queryHelperFunctions(false),
        "ir/aws_query_flatten_member.expected.ex");
  }

  @Test
  void serverQueryDecodeHelpersAwsAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirAwsQueryHelperIr.serverDecodeHelpers(false),
        "ir/aws_query_form_decode_aws.expected.ex");
  }

  @Test
  void queryHelpersAwsAreStructural() {
    for (ExFunction fn : ElixirAwsQueryHelperIr.queryHelperFunctions(false)) {
      ElixirIrTestSupport.assertStructural(fn);
    }
    String text = helpersAsString(ElixirAwsQueryHelperIr.queryHelperFunctions(false));
    assertThat(text).contains("when is_list(value) do");
    assertThat(text).contains("when is_map(value) do");
  }

  @Test
  void serverQueryDecodeHelpersAreStructural() {
    for (ExFunction fn : ElixirAwsQueryHelperIr.serverDecodeHelpers(false)) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  private static void assertGolden(List<ExFunction> functions, String resourcePath)
      throws IOException {
    assertThat(helpersAsString(functions)).isEqualTo(readExpectedString(resourcePath));
    for (ExFunction fn : functions) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  private static String helpersAsString(List<ExFunction> functions) {
    return functions.stream().map(ExFunction::asString).collect(Collectors.joining("\n\n"));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirAwsQueryIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
