package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErlTypeHeaderTest {
  @Test
  void basicTypesLines() throws IOException {
    ErlTypeHeader header = buildBasicTypes();
    assertThat(header.lines()).isEqualTo(readExpectedLines("ir/basic_types.expected.hrl"));
  }

  @Test
  void basicTypesAsString() throws IOException {
    ErlTypeHeader header = buildBasicTypes();
    assertThat(header.asString()).isEqualTo(readExpectedString("ir/basic_types.expected.hrl"));
  }

  private static ErlTypeHeader buildBasicTypes() {
    return ErlTypeHeader.typeHeader(
        "basic_types",
        List.of(
            ErlComment.comment("Record and type definitions for the basic_service_types model."),
            ErlComment.comment("")),
        List.of(
            new ErlRecordDef(
                "basic_item",
                List.of(
                    new ErlRecordFieldDef("name", "basic_string()"),
                    new ErlRecordFieldDef("count", "basic_integer() | undefined"))),
            new ErlTypeDef("basic_item", "#basic_item{}")));
  }

  private static List<String> readExpectedLines(String resourcePath) throws IOException {
    try (InputStream in =
        ErlTypeHeaderTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return Arrays.asList(text.split("\n", -1));
    }
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlTypeHeaderTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
