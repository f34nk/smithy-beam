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
    String http = manifest.expectFileString("runtime_http.erl");

    assertThat(http).contains("case maps:get(base_url, Config, undefined) of");
    assertThat(http).contains("GivenUrl ->");
    assertThat(http).contains("GivenUrl");
    assertThat(http).doesNotContain("Url -> Url");
    assertThat(http).contains("runtime_helpers:resolve_base_url(Config)");

    String helpers = manifest.expectFileString("runtime_helpers.erl");
    assertThat(helpers).contains("resolve_base_url(Config) ->");
    assertThat(helpers).contains("maps:get(endpoint_prefix, Config)");
  }

  @Test
  void elixirHttpDispatchFallsBackToResolveBaseUrl() {
    MockManifest manifest = runElixirClient();
    String http = manifest.expectFileString("runtime_http.ex");

    assertThat(http).contains("case Map.get(config, :base_url) do");
    assertThat(http).contains("RuntimeHelpers.resolve_base_url(config)");

    String helpers = manifest.expectFileString("runtime_helpers.ex");
    assertThat(helpers).contains("def resolve_base_url(config) do");
    assertThat(helpers).contains("Map.fetch!(config, :endpoint_prefix)");
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
