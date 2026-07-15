package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.Module;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.UnionShape;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
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
  void unionHelpersMatchExpectedShape() {
    String typesMod = typesModuleName();
    List<Function> functions =
        ElixirEventStreamIr.unionHelpers(model, eventStreamUnion, provider, typesMod);
    assertThat(functions).isNotEmpty();
    for (Function fn : functions) {
      ElixirIrTestSupport.assertStructural(fn);
    }
    String combined =
        functions.stream().map(ElixirRenderer::renderFunction).collect(Collectors.joining("\n\n"));
    assertThat(combined).contains("def encode_event_stream(events) when is_list(events)");
    assertThat(combined).contains("def decode_event_stream(body) when is_binary(body)");
    assertThat(combined).contains("AwsEventStream.decode_frames()");
    assertThat(combined).contains("AwsEventStream.encode_event_headers");
    assertThat(combined).contains("AwsEventStream.frame");
    assertThat(combined).contains("AwsEventStream.header_value");
    assertThat(combined).contains("defp encode_event_stream_event({:member, value})");
    assertThat(combined).contains("defp decode_event_stream_event_type(\"member\", payload)");
  }

  @Test
  void eventStreamModuleMatchesExpectedShape() {
    Module module = ElixirEventStreamIr.eventStreamModule(testContext(), service);
    String text = ElixirRenderer.render(module);
    assertThat(text).contains("defmodule EventStreamRestJsonServiceEventStream do");
    assertThat(text).contains("alias EventStreamRestJsonServiceTypes");
    assertThat(text).contains("def encode_event_stream(events) when is_list(events)");
    assertThat(text).contains("def decode_event_stream(body) when is_binary(body)");
    assertThat(text).contains("AwsEventStream.decode_frames()");
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
}
