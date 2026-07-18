package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.test.support.SerializeHookRecordingIntegration;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ClientProtocolIntegrationHookTest {

  private static final String SERVICE = "smithy.beam.demo.protocoljson#DemoRestJson";

  private static Model loadModel() {
    URL resource =
        ClientProtocolIntegrationHookTest.class.getResource(
            "/model/protocol_rest_json_fixture.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static PluginContext buildContext(Model model, MockManifest manifest) {
    ObjectNode settings =
        ObjectNode.builder().withMember("service", SERVICE).withMember("edition", "2026").build();
    return PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();
  }

  private static void runClientDirectedCodegen(PluginContext context) {
    CodegenDirector<ElixirWriter, ElixirIntegration, ElixirContext, BeamSettings> runner =
        new CodegenDirector<>();
    runner.directedCodegen(new ElixirClientDirectedCodegen());
    runner.integrationClass(ElixirIntegration.class);
    runner.fileManifest(context.getFileManifest());
    runner.integrationSettings(context.getSettings());
    context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);
    runner.integrationFinder(() -> List.of(new SerializeHookRecordingIntegration.Elixir()));
    runner.model(context.getModel());

    BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
    var resolvedService = settings.resolveService(context.getModel());
    runner.service(resolvedService);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings, context.getModel());

    runner.run();
  }

  @Test
  void elixirCustomizeProtocolSerializeRunsForRestJsonClient() {
    Model model = loadModel();
    MockManifest manifest = new MockManifest();
    PluginContext context = buildContext(model, manifest);

    runClientDirectedCodegen(context);

    assertThat(manifest.expectFileString("demo_rest_json_client.ex"))
        .contains("# serialize-hook-recording-integration");
  }

  @Test
  void elixirCustomizeProtocolDeserializeRunsForRestJsonClient() {
    Model model = loadModel();
    MockManifest manifest = new MockManifest();
    PluginContext context = buildContext(model, manifest);

    runClientDirectedCodegen(context);

    assertThat(manifest.expectFileString("demo_rest_json_client.ex"))
        .contains("# deserialize-hook-recording-integration");
  }
}
