package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.ErlangServerPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ErlangServerResponseEncodeTest {

  @Test
  void serverCodecEmitsResponseEncoderForEachOperation() {
    URL resource = getClass().getResource("/model/protocol_rest_json_fixture.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest manifest = new MockManifest();
    new ErlangServerPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
                        .withMember("edition", "2026")
                        .build())
                .build());

    String serverCodec = manifest.getFileString("demo_rest_json_rest_json_1.erl").orElse("");
    assertThat(serverCodec).contains("encode_describe_item_response(");
    assertThat(serverCodec).contains("encode_create_item_response(");
    assertThat(serverCodec).contains("#http_response{");
    assertThat(serverCodec).contains("status = 200");
    assertThat(serverCodec).contains("status = 201");
    assertThat(manifest.getFileString("runtime_helpers.erl")).isEmpty();

    String router = manifest.getFileString("demo_rest_json_router.erl").orElse("");
    assertThat(router).contains("runtime_helpers:parse_labels(");
  }
}
