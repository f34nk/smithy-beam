package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.ErlangClientPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ErlangDslGoldenPluginParityTest {

  private static Model loadModel() {
    URL resource = ErlangDslGoldenPluginParityTest.class.getResource("/model/golden_http.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static MockManifest runClientPlugin(Model model) {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.http#HttpService")
            .withMember("edition", "2026")
            .build();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());
    return manifest;
  }

  private static String readGolden(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangDslGoldenPluginParityTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      return stripTrailingNewline(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private static String stripTrailingNewline(String text) {
    if (text.endsWith("\n")) {
      return text.substring(0, text.length() - 1);
    }
    return text;
  }

  @Test
  void clientPluginCodecModuleMatchesDslGolden() throws IOException {
    MockManifest manifest = runClientPlugin(loadModel());
    String emitted =
        stripTrailingNewline(manifest.expectFileString("http_service_rest_json_1.erl"));
    assertThat(emitted)
        .isEqualTo(readGolden("golden/http_service_rest_json_1_client_codec.expected.erl"));
  }

  @Test
  void clientPluginTypesHeaderContainsStructureSlice() {
    MockManifest manifest = runClientPlugin(loadModel());
    String types = manifest.expectFileString("http_service_types.hrl");
    assertThat(types)
        .contains("-record(get_name_output, {")
        .contains("name :: name() | undefined")
        .contains("-type get_name_output() :: #get_name_output{}.");
  }

  @Disabled(
      "One-time golden capture helper; run locally then copy printed paths to test/resources/golden/")
  @Test
  void captureGoldens() {
    MockManifest manifest = runClientPlugin(loadModel());
    System.out.println("=== http_service_rest_json_1.erl ===");
    System.out.println(manifest.expectFileString("http_service_rest_json_1.erl"));
    System.out.println("=== http_service_types.hrl (structure slice marker) ===");
    System.out.println(manifest.expectFileString("http_service_types.hrl"));
  }
}
