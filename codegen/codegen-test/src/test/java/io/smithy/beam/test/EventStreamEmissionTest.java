package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class EventStreamEmissionTest {

    private static final String REST_JSON_SERVICE =
            "smithy.beam.test.eventstream#EventStreamRestJsonService";
    private static final String AWS_JSON_SERVICE =
            "smithy.beam.test.eventstream#EventStreamAwsJsonService";

    private Model eventStreamFixtureModel() {
        URL resource = EventStreamEmissionTest.class.getResource("/model/event_stream_fixture.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void erlangEventStreamModuleEmitsFramingHelpers() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(eventStreamFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", REST_JSON_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String eventStream = manifest.getFileString("event_stream_rest_json_service_event_stream.erl").orElse("");
        assertThat(eventStream).contains("-module(event_stream_rest_json_service_event_stream).");
        assertThat(eventStream).contains("encode_event_stream(");
        assertThat(eventStream).contains("decode_event_stream(");
        assertThat(eventStream).contains("aws_event_stream:frame(Headers, Payload)");
        assertThat(eventStream).contains("aws_event_stream:decode_frames(Body)");

        String codec = manifest.getFileString("event_stream_rest_json_service_rest_json_1.erl").orElse("");
        assertThat(codec).contains("event_stream_rest_json_service_event_stream:decode_event_stream(Body)");
    }

    @Test
    void erlangAwsJsonCodecUsesEventStreamFraming() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(eventStreamFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", AWS_JSON_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String codec = manifest.getFileString("event_stream_aws_json_service_aws_json_1_1.erl").orElse("");
        assertThat(codec).contains("event_stream_aws_json_service_event_stream:decode_event_stream(Body)");
    }

    @Test
    void elixirEventStreamModuleEmitsFramingHelpers() {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(eventStreamFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", REST_JSON_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String eventStream = manifest.getFileString("event_stream_rest_json_service_event_stream.ex").orElse("");
        assertThat(eventStream).contains("defmodule EventStreamRestJsonServiceEventStream");
        assertThat(eventStream).contains("def encode_event_stream(");
        assertThat(eventStream).contains("def decode_event_stream(");
        assertThat(eventStream).contains("AwsEventStream.frame(headers, payload)");
        assertThat(eventStream).contains("AwsEventStream.decode_frames()");
    }

    @Test
    void elixirAwsJsonCodecUsesEventStreamFraming() {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(eventStreamFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", AWS_JSON_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String codec = manifest.getFileString("event_stream_aws_json_service_aws_json_1_1.ex").orElse("");
        assertThat(codec).contains("EventStreamAwsJsonServiceEventStream.decode_event_stream");
    }
}
