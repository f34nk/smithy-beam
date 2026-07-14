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

class RetryEmissionTest {

  private static final String SERVICE = "smithy.beam.demo.error_shapes#ErrorFixtureService";

  private Model errorFixtureModel() {
    URL resource = RetryEmissionTest.class.getResource("/model/error_shapes.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void erlangRetryModuleWrapsRetryableErrors() {
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
    assertThat(client).contains("should_retry({error, #not_found_error{}}) -> true;");
    assertThat(client).contains("should_retry({error, #throttling_error{}}) -> true;");
    assertThat(client).contains("runtime_http:with_retry(");
  }

  @Test
  void elixirRetryModuleWrapsRetryableErrors() {
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

    assertThat(manifest.getFileString("error_fixture_service_retry.ex")).isEmpty();

    String client = manifest.expectFileString("error_fixture_service_client.ex");
    assertThat(client)
        .contains("def should_retry?({:error, %ErrorFixtureServiceTypes.NotFoundError{}})");
    assertThat(client)
        .contains("def should_retry?({:error, %ErrorFixtureServiceTypes.ThrottlingError{}})");
    assertThat(client).contains("RuntimeHttp.with_retry(");
    assertThat(client).contains("{:should_retry, &ErrorFixtureServiceClient.should_retry?/1}");
  }
}
