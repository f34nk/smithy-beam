package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangServerPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangServerResponseEncodeTest {

    @Test
    void serverCodecEmitsResponseEncoderForEachOperation() {
        URL resource = getClass().getResource("/model/protocol_rest_json_fixture.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        new ErlangServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service",
                                "smithy.beam.demo.protocoljson#DemoRestJson")
                        .withMember("edition", "2026")
                        .withMember("protocol", "aws.protocols#restJson1")
                        .build())
                .build());

        String serverCodec = manifest.getFileString("protocoljson_server_rest_json_1.erl")
                .orElse("");
        assertThat(serverCodec).contains("encode_describe_item_response(");
        assertThat(serverCodec).contains("encode_create_item_response(");
        assertThat(serverCodec).contains("#http_response{");
        assertThat(serverCodec).contains("status = 200");
        assertThat(serverCodec).contains("status = 201");
        assertThat(manifest.getFileString("runtime_helpers.erl").orElse(""))
                .contains("-module(runtime_helpers).")
                .contains("parse_labels(Path, Template)");
    }
}
