package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.test.support.RecordingElixirIntegration;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ElixirIntegrationSectionsTest {

  private static final String TYPES_FILE = "basic_service_types.ex";

  private static Model loadModel() {
    URL resource = ElixirIntegrationSectionsTest.class.getResource("/model/basic.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static Model loadModel(String resourcePath) {
    URL resource = ElixirIntegrationSectionsTest.class.getResource(resourcePath);
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static PluginContext buildContext(Model model, MockManifest manifest, String service) {
    ObjectNode settings =
        ObjectNode.builder().withMember("service", service).withMember("edition", "2026").build();
    return PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();
  }

  private static PluginContext buildContext(Model model, MockManifest manifest) {
    return buildContext(model, manifest, "smithy.beam.demo.basic#BasicService");
  }

  private static void runClientDirectedCodegen(PluginContext context) {
    CodegenDirector<ElixirWriter, ElixirIntegration, ElixirContext, BeamSettings> runner =
        new CodegenDirector<>();
    runner.directedCodegen(new ElixirClientDirectedCodegen());
    runner.integrationClass(ElixirIntegration.class);
    runner.fileManifest(context.getFileManifest());
    runner.integrationSettings(context.getSettings());
    context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);
    runner.integrationFinder(() -> List.of(new RecordingElixirIntegration()));
    runner.model(context.getModel());

    BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
    var resolvedService = settings.resolveService(context.getModel());
    runner.service(resolvedService);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

    runner.run();
  }

  @Test
  void recordingIntegrationMarkerAppearsInEmittedTypesFile() {
    Model model = loadModel();
    MockManifest manifest = new MockManifest();
    PluginContext context = buildContext(model, manifest);

    CodegenDirector<ElixirWriter, ElixirIntegration, ElixirContext, BeamSettings> runner =
        new CodegenDirector<>();
    runner.directedCodegen(new ElixirDirectedCodegen());
    runner.integrationClass(ElixirIntegration.class);
    runner.fileManifest(context.getFileManifest());
    runner.integrationSettings(context.getSettings());
    context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);
    runner.integrationFinder(() -> List.of(new RecordingElixirIntegration()));
    runner.model(context.getModel());

    BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
    var resolvedService = settings.resolveService(context.getModel());
    runner.service(resolvedService);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

    runner.run();

    assertThat(manifest.expectFileString(TYPES_FILE))
        .contains("# recording-elixir-integration was here");
  }

  @Test
  void generatedDocumentationSectionReceivesStructureDocs() {
    URL resource =
        ElixirIntegrationSectionsTest.class.getResource("/model/documented_types.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.documented_types#DocumentedTypesService")
            .withMember("edition", "2026")
            .build();
    PluginContext context =
        PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();

    CodegenDirector<ElixirWriter, ElixirIntegration, ElixirContext, BeamSettings> runner =
        new CodegenDirector<>();
    runner.directedCodegen(new ElixirDirectedCodegen());
    runner.integrationClass(ElixirIntegration.class);
    runner.fileManifest(context.getFileManifest());
    runner.integrationSettings(context.getSettings());
    context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);
    runner.integrationFinder(() -> List.of(new RecordingElixirIntegration()));
    runner.model(context.getModel());

    BeamSettings beamSettings = runner.settings(BeamSettings.class, context.getSettings());
    var resolvedService = beamSettings.resolveService(context.getModel());
    runner.service(resolvedService);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, beamSettings);

    runner.run();

    String content = manifest.expectFileString("documented_types_service_types.ex");
    assertThat(content).contains("# recording-elixir-integration was here");
    assertThat(content).contains("A documented structure with member docs.");
  }

  @Test
  void clientDirectedCodegenEmitsWireModulesWhenServiceDeclaresRestJson1() {
    Model model = loadModel("/model/protocol_rest_json_fixture.smithy");
    MockManifest manifest = new MockManifest();
    PluginContext context =
        buildContext(model, manifest, "smithy.beam.demo.protocoljson#DemoRestJson");

    runClientDirectedCodegen(context);

    assertThat(manifest.getFileString("demo_rest_json_rest_json_1.ex")).isPresent();
    assertThat(manifest.getFileString("runtime_http.ex")).isPresent();
    assertThat(manifest.expectFileString("demo_rest_json_client.ex"))
        .contains("# recording-elixir-integration was here");
  }

  @Test
  void clientDirectedCodegenOmitsWireModulesWhenServiceHasNoProtocolTrait() {
    Model model = loadModel("/model/dedicated_operation_io.smithy");
    MockManifest manifest = new MockManifest();
    PluginContext context =
        buildContext(model, manifest, "smithy.beam.demo.dedicated_io#DedicatedIoService");

    runClientDirectedCodegen(context);

    assertThat(manifest.getFileString("dedicated_io_service_rest_json_1.ex")).isEmpty();
    assertThat(manifest.getFileString("runtime_http.ex")).isPresent();
    assertThat(manifest.expectFileString("dedicated_io_service_client.ex"))
        .contains("# recording-elixir-integration was here");
  }
}
