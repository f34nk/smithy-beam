package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.erlang.Function;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.UnionShape;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
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
    DslGoldenAssertions.assertGolden(
        ErlangEventStreamDsl.encodeEventHeaders(),
        "dsl/event_stream_encode_event_headers.expected.erl");
  }

  @Test
  void headerValueAsStringMatchesGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangEventStreamDsl.headerValue(), "dsl/event_stream_header_value.expected.erl");
  }

  @Test
  void unionHelpersAsStringMatchesGolden() throws IOException {
    List<Function> functions = ErlangEventStreamDsl.unionHelpers(model, eventStreamUnion, provider);
    assertThat(functions).hasSize(5);
    for (Function fn : functions) {
      assertThat(fn.name()).isNotBlank();
      assertThat(fn.clauses()).isNotEmpty();
    }
    String combined = DslGoldenAssertions.renderFunctions(functions);
    assertThat(combined)
        .isEqualTo(
            DslGoldenAssertions.readExpectedString("dsl/event_stream_union_helpers.expected.erl"));
  }
}
