package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.elixir.ElixirServerPlugin;
import io.smithy.beam.elixir.ElixirTypesPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import io.smithy.beam.erlang.ErlangServerPlugin;
import io.smithy.beam.erlang.ErlangTypesPlugin;
import io.smithy.beam.test.support.TestCustomProtocolIntegration;
import java.net.URL;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ModelProtocolWireTest {

  private static Model loadModel(String resourcePath) {
    URL resource = ModelProtocolWireTest.class.getResource(resourcePath);
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static ObjectNode settings(String service) {
    return settings(service, null);
  }

  private static ObjectNode settings(String service, String protocol) {
    ObjectNode.Builder builder =
        ObjectNode.builder().withMember("service", service).withMember("edition", "2026");
    if (protocol != null) {
      builder.withMember("protocol", protocol);
    }
    return builder.build();
  }

  private enum ClientServerPlugin {
    ERLANG_CLIENT(ctx -> new ErlangClientPlugin().execute(ctx)),
    ERLANG_SERVER(ctx -> new ErlangServerPlugin().execute(ctx)),
    ELIXIR_CLIENT(ctx -> new ElixirClientPlugin().execute(ctx)),
    ELIXIR_SERVER(ctx -> new ElixirServerPlugin().execute(ctx));

    private final Consumer<PluginContext> runner;

    ClientServerPlugin(Consumer<PluginContext> runner) {
      this.runner = runner;
    }

    void execute(PluginContext context) {
      runner.accept(context);
    }

    boolean erlang() {
      return name().startsWith("ERLANG");
    }

    boolean client() {
      return name().endsWith("CLIENT");
    }

    String ext() {
      return erlang() ? "erl" : "ex";
    }
  }

  @Test
  void restJson1ServiceEmitsWireModulesWithEditionAndServiceSettingsOnly() {
    Model model = loadModel("/model/protocol_rest_json_fixture.smithy");
    ObjectNode pluginSettings = settings("smithy.beam.demo.protocoljson#DemoRestJson");

    for (ClientServerPlugin plugin : ClientServerPlugin.values()) {
      MockManifest manifest = new MockManifest();
      plugin.execute(
          PluginContext.builder()
              .model(model)
              .fileManifest(manifest)
              .settings(pluginSettings)
              .build());

      String ext = plugin.ext();
      if (plugin.client()) {
        assertThat(manifest.getFileString("demo_rest_json_rest_json_1." + ext)).isPresent();
        if (plugin.erlang()) {
          assertThat(manifest.getFileString("runtime_http." + ext)).isEmpty();
        } else {
          assertThat(manifest.getFileString("runtime_http." + ext)).isPresent();
        }
      } else {
        assertThat(manifest.getFileString("demo_rest_json_router." + ext)).isPresent();
        assertThat(manifest.getFileString("demo_rest_json_rest_json_1." + ext)).isPresent();
      }
    }
  }

  @Test
  void serviceWithoutProtocolTraitEmitsStubModulesOnly() {
    Model model = loadModel("/model/dedicated_operation_io.smithy");
    ObjectNode pluginSettings = settings("smithy.beam.demo.dedicated_io#DedicatedIoService");

    for (ClientServerPlugin plugin : ClientServerPlugin.values()) {
      MockManifest manifest = new MockManifest();
      plugin.execute(
          PluginContext.builder()
              .model(model)
              .fileManifest(manifest)
              .settings(pluginSettings)
              .build());

      String ext = plugin.ext();
      if (plugin.client()) {
        assertThat(manifest.getFileString("dedicated_io_service_client." + ext)).isPresent();
      } else {
        assertThat(manifest.getFileString("dedicated_io_service_server." + ext)).isPresent();
      }
      assertThat(manifest.getFileString("dedicated_io_service_rest_json_1." + ext)).isEmpty();
      assertThat(manifest.getFileString("dedicated_io_service_router." + ext)).isEmpty();
      if (plugin.client() && plugin.erlang()) {
        assertThat(manifest.getFileString("runtime_http." + ext)).isEmpty();
      } else if (plugin.client()) {
        assertThat(manifest.getFileString("runtime_http." + ext)).isPresent();
      } else {
        assertThat(manifest.getFileString("runtime_http." + ext)).isEmpty();
      }
    }
  }

  @Test
  void unsupportedModelProtocolFailsWithCodegenException() {
    Model model = loadModel("/model/multi_service.smithy");
    ObjectNode pluginSettings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.multi#ServiceA")
            .withMember("edition", "2026")
            .build();

    for (ClientServerPlugin plugin : ClientServerPlugin.values()) {
      MockManifest manifest = new MockManifest();
      assertThatThrownBy(
              () ->
                  plugin.execute(
                      PluginContext.builder()
                          .model(model)
                          .fileManifest(manifest)
                          .settings(pluginSettings)
                          .build()))
          .isInstanceOf(CodegenException.class)
          .hasMessageContaining("No BeamProtocolCodegen registered for protocol trait")
          .hasMessageContaining("smithy.beam.demo.multi#TestProtocol");
    }
  }

  @Test
  void multipleModelProtocolTraitsFailWithCodegenException() {
    Model model = loadModel("/model/multi_protocol.smithy");
    ObjectNode pluginSettings = settings("smithy.beam.demo.multi_protocol#DualProtocolService");

    for (ClientServerPlugin plugin : ClientServerPlugin.values()) {
      MockManifest manifest = new MockManifest();
      assertThatThrownBy(
              () ->
                  plugin.execute(
                      PluginContext.builder()
                          .model(model)
                          .fileManifest(manifest)
                          .settings(pluginSettings)
                          .build()))
          .isInstanceOf(CodegenException.class)
          .hasMessageContaining("declares multiple protocol traits");
    }
  }

  @Test
  void explicitProtocolSettingOverridesMissingModelTrait() {
    Model model = loadModel("/model/dedicated_operation_io.smithy");
    ObjectNode pluginSettings =
        settings(
            "smithy.beam.demo.dedicated_io#DedicatedIoService",
            TestCustomProtocolIntegration.TEST_CUSTOM_PROTOCOL.toString());

    MockManifest manifest = new MockManifest();
    ClientServerPlugin.ERLANG_CLIENT.execute(
        PluginContext.builder()
            .model(model)
            .fileManifest(manifest)
            .pluginClassLoader(ModelProtocolWireTest.class.getClassLoader())
            .settings(pluginSettings)
            .build());

    assertThat(manifest.getFileString("dedicated_io_service_test_custom_protocol.erl")).isPresent();
    assertThat(manifest.expectFileString("dedicated_io_service_client.erl"))
        .doesNotContain("not_implemented");
  }

  @Test
  void explicitProtocolSettingSelectsOneOfMultipleModelTraits() {
    Model model = loadModel("/model/multi_protocol.smithy");
    ObjectNode pluginSettings =
        settings(
            "smithy.beam.demo.multi_protocol#DualProtocolService",
            TestCustomProtocolIntegration.TEST_CUSTOM_PROTOCOL.toString());

    MockManifest manifest = new MockManifest();
    ClientServerPlugin.ERLANG_CLIENT.execute(
        PluginContext.builder()
            .model(model)
            .fileManifest(manifest)
            .pluginClassLoader(ModelProtocolWireTest.class.getClassLoader())
            .settings(pluginSettings)
            .build());

    assertThat(manifest.getFileString("dual_protocol_service_test_custom_protocol.erl"))
        .isPresent();
    assertThat(manifest.expectFileString("dual_protocol_service_client.erl"))
        .doesNotContain("not_implemented");
  }

  @Test
  void typesPluginOnRestJson1ServiceEmitsTypesOnlyWithoutWireModules() {
    Model model = loadModel("/model/reserved_words.smithy");
    ObjectNode pluginSettings = settings("smithy.beam.demo.reserved#ReservedService");

    MockManifest erlangManifest = new MockManifest();
    new ErlangTypesPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(erlangManifest)
                .settings(pluginSettings)
                .build());
    assertThat(erlangManifest.getFileString("reserved_service_types.hrl")).isPresent();
    assertThat(erlangManifest.getFileString("reserved_service_rest_json_1.erl")).isEmpty();
    assertThat(erlangManifest.getFileString("reserved_service_router.erl")).isEmpty();
    assertThat(erlangManifest.getFileString("runtime_http.erl")).isEmpty();

    MockManifest elixirManifest = new MockManifest();
    new ElixirTypesPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(elixirManifest)
                .settings(pluginSettings)
                .build());
    assertThat(elixirManifest.getFileString("reserved_service_types.ex")).isPresent();
    assertThat(elixirManifest.getFileString("reserved_service_rest_json_1.ex")).isEmpty();
    assertThat(elixirManifest.getFileString("reserved_service_router.ex")).isEmpty();
    assertThat(elixirManifest.getFileString("runtime_http.ex")).isEmpty();
  }
}
