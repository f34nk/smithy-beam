package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirServerPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ElixirAwsJsonServerCodecTest {

  @Test
  void awsJson10ServerEmitsDecodeAndEncodeFunctions() {
    Model model =
        Model.assembler()
            .addImport(AwsJson10CodecTest.class.getResource("/model/aws_json_1_0_fixture.smithy"))
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest = new MockManifest();
    new ElixirServerPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", "smithy.beam.test.awsjson10#Json10Service")
                        .withMember("edition", "2026")
                        .build())
                .build());
    String serverCodec =
        manifest.getFiles().stream()
            .map(p -> p.getFileName().toString())
            .filter(name -> name.endsWith("aws_json_1_0.ex"))
            .findFirst()
            .flatMap(manifest::getFileString)
            .orElse("");
    assertThat(serverCodec).contains("def decode_get_user_request");
    assertThat(serverCodec).contains("def encode_get_user_response");
  }

  @Test
  void awsJson11ServerEmitsDecodeAndEncodeFunctions() {
    Model model =
        Model.assembler()
            .addImport(AwsJson11CodecTest.class.getResource("/model/aws_json_1_1_fixture.smithy"))
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest = new MockManifest();
    new ElixirServerPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", "smithy.beam.test.awsjson11#Json11Service")
                        .withMember("edition", "2026")
                        .build())
                .build());
    String serverCodec =
        manifest.getFiles().stream()
            .map(p -> p.getFileName().toString())
            .filter(name -> name.endsWith("aws_json_1_1.ex"))
            .findFirst()
            .flatMap(manifest::getFileString)
            .orElse("");
    assertThat(serverCodec).contains("def decode_get_user_request");
    assertThat(serverCodec).contains("def encode_get_user_response");
  }
}
