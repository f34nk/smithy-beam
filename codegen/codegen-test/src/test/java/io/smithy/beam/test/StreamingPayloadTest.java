package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingPayloadTest {

    private static final String MODEL = """
            $version: "2"
            namespace smithy.beam.test.streaming

            use aws.protocols#restJson1
            use smithy.api#httpPayload
            use smithy.api#streaming

            @restJson1
            service StreamingService {
                version: "2026"
                operations: [PutStreamingBody, GetStreamingBody]
            }

            @streaming
            blob StreamingBlob

            @http(method: "PUT", uri: "/stream")
            operation PutStreamingBody {
                input: PutStreamingBodyInput
                output: PutStreamingBodyOutput
            }

            structure PutStreamingBodyInput {
                @httpPayload
                body: StreamingBlob
            }

            structure PutStreamingBodyOutput {
                ok: Boolean
            }

            @http(method: "GET", uri: "/stream")
            @readonly
            operation GetStreamingBody {
                input: GetStreamingBodyInput
                output: GetStreamingBodyOutput
            }

            structure GetStreamingBodyInput {}

            structure GetStreamingBodyOutput {
                @httpPayload
                body: StreamingBlob
            }
            """;

    private static Model loadModel() {
        return Model.assembler()
                .addUnparsedModel("test.smithy", MODEL)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static MockManifest runErlangPlugin(Model model) {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.beam.test.streaming#StreamingService")
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    private static MockManifest runElixirPlugin(Model model) {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.beam.test.streaming#StreamingService")
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    @Test
    void erlangRuntimeTypesIncludeStreamField() {
        String types = runErlangPlugin(loadModel())
                .getFileString("runtime_types.hrl")
                .orElse("");
        assertThat(types).contains("stream = undefined");
    }

    @Test
    void erlangRequestEncoderUsesStreamField() {
        String codec = runErlangPlugin(loadModel())
                .getFileString("streaming_service_rest_json_1.erl")
                .orElse("");
        assertThat(codec).contains("encode_put_streaming_body_request(");
        assertThat(codec).contains("stream = Stream");
    }

    @Test
    void erlangResponseDecoderUsesStreamField() {
        String codec = runErlangPlugin(loadModel())
                .getFileString("streaming_service_rest_json_1.erl")
                .orElse("");
        assertThat(codec).contains("decode_get_streaming_body_response(");
        assertThat(codec).contains("stream = Stream");
        assertThat(codec).contains("body = Stream");
    }

    @Test
    void elixirRuntimeTypesIncludeStreamField() {
        String types = runElixirPlugin(loadModel())
                .getFileString("runtime_types.ex")
                .orElse("");
        assertThat(types).contains("stream: nil");
    }

    @Test
    void elixirRequestEncoderUsesStreamField() {
        String codec = runElixirPlugin(loadModel())
                .getFileString("streaming_service_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("def encode_put_streaming_body_request(");
        assertThat(codec).contains("stream: stream");
    }

    @Test
    void elixirResponseDecoderUsesStreamField() {
        String codec = runElixirPlugin(loadModel())
                .getFileString("streaming_service_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("def decode_get_streaming_body_response(");
        assertThat(codec).contains("stream: stream");
        assertThat(codec).contains("body: stream");
    }
}
