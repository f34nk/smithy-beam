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

class RetryWiringTest {

  private static final String SERVICE = "smithy.beam.demo.error_shapes#ErrorFixtureService";

  private Model errorFixtureModel() {
    URL resource = RetryEmissionTest.class.getResource("/model/error_shapes.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void erlangClientWrapsOperationsWithRetryableErrors() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(errorFixtureModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    String client = manifest.expectFileString("error_fixture_service_client.erl");
    assertThat(client).contains("RetryOpts = maps:get(retry, Config, #{})");
    assertThat(client).contains("error_fixture_service_retry:with_retry(");
    assertThat(client).contains("fun() ->");
    assertThat(client).contains("get_item(Config, Input) ->");
  }

  @Test
  void erlangBasicClientWrapsOnlyRetryableOperations() {
    URL resource = RetryWiringTest.class.getResource("/model/basic.smithy");
    if (resource == null) {
      resource = ErlangClientPluginTest.class.getResource("/model/basic.smithy");
    }
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();

    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", "smithy.beam.demo.basic#BasicService")
                        .withMember("edition", "2026")
                        .build())
                .build());

    String client = manifest.expectFileString("basic_service_client.erl");
    assertThat(client).contains("basic_service_retry:with_retry(");
    assertThat(client).contains("fun() ->");
    assertThat(client).contains("get_type_closure(Config, Input) ->");
    assertThat(client).doesNotContain("list_basic_items(Config, Input) ->\n    RetryOpts");
  }

  @Test
  void elixirClientWrapsOperationsWithRetryableErrors() {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(errorFixtureModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    String client = manifest.expectFileString("error_fixture_service_client.ex");
    assertThat(client).contains("retry_opts = Map.get(config, :retry, [])");
    assertThat(client).contains("ErrorFixtureServiceRetry.with_retry(");
    assertThat(client).contains("fn ->");
    assertThat(client).contains("def get_item(");
  }
}
