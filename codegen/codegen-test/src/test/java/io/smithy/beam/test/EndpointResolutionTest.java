package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;

class EndpointResolutionTest {

  private static final ShapeId SERVICE =
      ShapeId.from("smithy.beam.test.awsservice#AwsMetadataService");

  private static Model loadAwsModel() {
    URL resource = EndpointResolutionTest.class.getResource("/model/aws_service_metadata.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void erlangHttpDispatchFallsBackToResolveBaseUrl() {
    MockManifest manifest = runErlangClient();

    String client = manifest.expectFileString("aws_metadata_service_client.erl");
    assertThat(client).doesNotContain("default_config()");
  }

  @Test
  void elixirRuntimeUtilsExposesEndpointHostFromConfig() {
    MockManifest manifest = runElixirClient();
    String runtimeUtils = manifest.expectFileString("runtime_utils.ex");

    assertThat(runtimeUtils).contains("def endpoint_host_from_config(config) do");
    assertThat(runtimeUtils).contains("Map.get(config, :endpoint_prefix)");
  }

  private static MockManifest runErlangClient() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(loadAwsModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", SERVICE.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }

  private static MockManifest runElixirClient() {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(loadAwsModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", SERVICE.toString())
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }
}
