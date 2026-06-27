package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.erlang.ErlFunction;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.UnionShape;

class ErlangEventStreamIrTest {
  private static Model model;
  private static UnionShape eventStreamUnion;
  private static ErlangSymbolProvider provider;

  @BeforeAll
  static void setupEventStreamFixture() throws Exception {
    URL resource = ErlangEventStreamIrTest.class.getResource("/model/event_stream_fixture.smithy");
    model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.eventstream#EventStreamRestJsonService"),
            ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    provider =
        new ErlangSymbolProvider(
            settings, model, service, "event_stream_types.hrl", BeamCodegenKind.TYPES);
    eventStreamUnion =
        model.expectShape(
            ShapeId.from("smithy.beam.test.eventstream#EventStream"), UnionShape.class);
  }

  @Test
  void encodeEventHeadersAsStringMatchesGolden() throws IOException {
    assertThat(ErlangEventStreamIr.encodeEventHeaders().asString())
        .isEqualTo(readExpectedString("ir/event_stream_encode_event_headers.expected.erl"));
  }

  @Test
  void headerValueAsStringMatchesGolden() throws IOException {
    assertThat(ErlangEventStreamIr.headerValue().asString())
        .isEqualTo(readExpectedString("ir/event_stream_header_value.expected.erl"));
  }

  @Test
  void unionHelpersAsStringMatchesGolden() throws IOException {
    List<ErlFunction> functions =
        ErlangEventStreamIr.unionHelpers(model, eventStreamUnion, provider);
    assertThat(functions).hasSize(5);
    for (ErlFunction fn : functions) {
      assertThat(fn.name()).isNotBlank();
      assertThat(fn.clauses()).isNotEmpty();
    }
    String combined =
        functions.stream().map(ErlFunction::asString).collect(Collectors.joining("\n\n"));
    assertThat(combined)
        .isEqualTo(readExpectedString("ir/event_stream_union_helpers.expected.erl"));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangEventStreamIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
