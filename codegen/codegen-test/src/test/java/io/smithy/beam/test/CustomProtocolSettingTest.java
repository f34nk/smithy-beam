package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import io.smithy.beam.test.support.TestCustomProtocolIntegration;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;

class CustomProtocolSettingTest {

  private static Model loadModel(String resourcePath) {
    URL resource = CustomProtocolSettingTest.class.getResource(resourcePath);
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void explicitProtocolSettingEmitsCodecAndWireOperationStub() {
    Model model = loadModel("/model/dedicated_operation_io.smithy");
    MockManifest manifest = new MockManifest();

    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .pluginClassLoader(CustomProtocolSettingTest.class.getClassLoader())
                .settings(
                    software.amazon.smithy.model.node.ObjectNode.builder()
                        .withMember("service", "smithy.beam.demo.dedicated_io#DedicatedIoService")
                        .withMember("edition", "2026")
                        .withMember(
                            "protocol",
                            TestCustomProtocolIntegration.TEST_CUSTOM_PROTOCOL.toString())
                        .build())
                .build());

    assertThat(manifest.getFileString("dedicated_io_service_test_custom_protocol.erl")).isPresent();
    String client = manifest.expectFileString("dedicated_io_service_client.erl");
    assertThat(client)
        .contains("dedicated_io_service_test_custom_protocol:encode_health_check_request");
    assertThat(client).doesNotContain("not_implemented");
  }

  @Test
  void elixirExplicitProtocolSettingEmitsCodecAndWireOperationStub() {
    Model model = loadModel("/model/dedicated_operation_io.smithy");
    MockManifest manifest = new MockManifest();

    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .pluginClassLoader(CustomProtocolSettingTest.class.getClassLoader())
                .settings(
                    software.amazon.smithy.model.node.ObjectNode.builder()
                        .withMember("service", "smithy.beam.demo.dedicated_io#DedicatedIoService")
                        .withMember("edition", "2026")
                        .withMember(
                            "protocol",
                            TestCustomProtocolIntegration.TEST_CUSTOM_PROTOCOL.toString())
                        .build())
                .build());

    assertThat(manifest.getFileString("dedicated_io_service_test_custom_protocol.ex")).isPresent();
    String client = manifest.expectFileString("dedicated_io_service_client.ex");
    assertThat(client).contains("DedicatedIoServiceTestCustomProtocol.encode_health_check_request");
    assertThat(client).doesNotContain(":not_implemented");
  }
}
