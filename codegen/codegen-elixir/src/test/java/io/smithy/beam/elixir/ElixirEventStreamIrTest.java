package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.UnionShape;

class ElixirEventStreamIrTest {
  private static Model model;
  private static ServiceShape service;
  private static UnionShape eventStreamUnion;
  private static ElixirSymbolProvider provider;

  @BeforeAll
  static void setupEventStreamFixture() {
    model = eventStreamModel();
    service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.eventstream#EventStreamRestJsonService"),
            ServiceShape.class);
    eventStreamUnion =
        model.expectShape(
            ShapeId.from("smithy.beam.test.eventstream#EventStream"), UnionShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    provider =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            "event_stream_types.ex",
            ElixirSymbolProvider.toModuleName("EventStreamTypes"),
            BeamCodegenKind.TYPES);
  }

  @Test
  void encodeEventHeadersAsStringMatchesGolden() throws IOException {
    assertThat(ElixirEventStreamIr.encodeEventHeaders().asString())
        .isEqualTo(readExpectedString("ir/event_stream_encode_event_headers.expected.ex"));
  }

  @Test
  void headerValueAsStringMatchesGolden() throws IOException {
    assertThat(ElixirEventStreamIr.headerValue().asString())
        .isEqualTo(readExpectedString("ir/event_stream_header_value.expected.ex"));
  }

  @Test
  void unionHelpersAsStringMatchesGolden() throws IOException {
    String typesMod = typesModuleName();
    List<ExFunction> functions =
        ElixirEventStreamIr.unionHelpers(model, eventStreamUnion, provider, typesMod);
    assertThat(functions).hasSize(5);
    for (ExFunction fn : functions) {
      ElixirIrTestSupport.assertStructural(fn);
    }
    String combined =
        functions.stream().map(ExFunction::asString).collect(Collectors.joining("\n\n"));
    assertThat(combined).isEqualTo(readExpectedString("ir/event_stream_union_helpers.expected.ex"));
  }

  @Test
  void eventStreamModuleAsStringMatchesGolden() throws IOException {
    ExModule module = ElixirEventStreamIr.eventStreamModule(testContext(), service);
    assertThat(module.asString())
        .isEqualTo(readExpectedString("ir/event_stream_module.expected.ex"));
  }

  private static String typesModuleName() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    return ElixirSymbolProvider.toModuleName(
        new io.smithy.beam.core.BeamElixirLayout(settings, service.getId().getNamespace(), service)
            .typesModuleName());
  }

  private static ElixirContext testContext() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    MockManifest manifest = new MockManifest();
    return new ElixirContext(
        model,
        settings,
        provider,
        manifest,
        new WriterDelegator<>(manifest, provider, ElixirWriter.factory("event_stream")),
        List.of(),
        service,
        BeamHttpBindings.from(model),
        null,
        null,
        "event_stream",
        "event_stream.ex");
  }

  private static Model eventStreamModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.test.eventstream

                use aws.protocols#restJson1
                use smithy.api#httpPayload
                use smithy.api#streaming

                @restJson1
                service EventStreamRestJsonService {
                    version: "2026"
                    operations: [StreamEvents]
                }

                @http(method: "POST", uri: "/events")
                operation StreamEvents {
                    input: StreamEventsInput
                    output: StreamEventsOutput
                }

                structure StreamEventsInput {}

                structure StreamEventsOutput {
                    @httpPayload
                    events: EventStream
                }

                @streaming
                union EventStream {
                    member: MemberEvent
                }

                structure MemberEvent {
                    value: String
                }
                """;
    return Model.assembler()
        .addUnparsedModel("event_stream.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirEventStreamIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
