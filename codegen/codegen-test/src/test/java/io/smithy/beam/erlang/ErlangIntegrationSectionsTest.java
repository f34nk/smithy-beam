package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.test.support.RecordingErlangIntegration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangIntegrationSectionsTest {

    private static final String TYPES_FILE = "basic_types.hrl";

    private static Model loadModel() {
        URL resource = ErlangIntegrationSectionsTest.class.getResource("/model/basic.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static Model loadModel(String resourcePath) {
        URL resource = ErlangIntegrationSectionsTest.class.getResource(resourcePath);
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static PluginContext buildContext(Model model, MockManifest manifest, String service) {
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", service)
                .withMember("edition", "2026")
                .build();
        return PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build();
    }

    private static PluginContext buildContext(Model model, MockManifest manifest) {
        return buildContext(model, manifest, "smithy.beam.demo.basic#BasicService");
    }

    private static void runClientDirectedCodegen(PluginContext context) {
        CodegenDirector<ErlangWriter, ErlangIntegration, ErlangContext, BeamSettings> runner =
                new CodegenDirector<>();
        runner.directedCodegen(new ErlangClientDirectedCodegen());
        runner.integrationClass(ErlangIntegration.class);
        runner.fileManifest(context.getFileManifest());
        runner.integrationSettings(context.getSettings());
        context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);
        runner.integrationFinder(() -> List.of(new RecordingErlangIntegration()));
        runner.model(context.getModel());

        BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
        var resolvedService = settings.resolveService(context.getModel());
        runner.service(resolvedService);

        BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

        runner.run();
    }

    @Test
    void recordingIntegrationMarkerAppearsInEmittedTypesHeader() {
        Model model = loadModel();
        MockManifest manifest = new MockManifest();
        PluginContext context = buildContext(model, manifest);

        CodegenDirector<ErlangWriter, ErlangIntegration, ErlangContext, BeamSettings> runner =
                new CodegenDirector<>();
        runner.directedCodegen(new ErlangDirectedCodegen());
        runner.integrationClass(ErlangIntegration.class);
        runner.fileManifest(context.getFileManifest());
        runner.integrationSettings(context.getSettings());
        context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);
        runner.integrationFinder(() -> List.of(new RecordingErlangIntegration()));
        runner.model(context.getModel());

        BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
        var resolvedService = settings.resolveService(context.getModel());
        runner.service(resolvedService);

        BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

        runner.run();

        assertThat(manifest.expectFileString(TYPES_FILE))
                .contains("%% recording-erlang-integration was here");
    }

    @Test
    void generatedDocumentationSectionReceivesStructureDocs() {
        URL resource = ErlangIntegrationSectionsTest.class.getResource("/model/documented_types.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.documented_types#DocumentedTypesService")
                .withMember("edition", "2026")
                .build();
        PluginContext context = PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build();

        CodegenDirector<ErlangWriter, ErlangIntegration, ErlangContext, BeamSettings> runner =
                new CodegenDirector<>();
        runner.directedCodegen(new ErlangDirectedCodegen());
        runner.integrationClass(ErlangIntegration.class);
        runner.fileManifest(context.getFileManifest());
        runner.integrationSettings(context.getSettings());
        context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);
        runner.integrationFinder(() -> List.of(new RecordingErlangIntegration()));
        runner.model(context.getModel());

        BeamSettings beamSettings = runner.settings(BeamSettings.class, context.getSettings());
        var resolvedService = beamSettings.resolveService(context.getModel());
        runner.service(resolvedService);

        BeamCodegenTransforms.applySharedCodegenTransforms(runner, beamSettings);

        runner.run();

        String content = manifest.expectFileString("documented_types_types.hrl");
        assertThat(content).contains("recording-erlang-integration was here");
        assertThat(content).contains("A documented structure with member docs.");
    }

    @Test
    void clientDirectedCodegenEmitsWireModulesWhenServiceDeclaresRestJson1() {
        Model model = loadModel("/model/protocol_rest_json_fixture.smithy");
        MockManifest manifest = new MockManifest();
        PluginContext context = buildContext(
                model, manifest, "smithy.beam.demo.protocoljson#DemoRestJson");

        runClientDirectedCodegen(context);

        assertThat(manifest.getFileString("protocoljson_service_rest_json_1.erl"))
                .isPresent();
        assertThat(manifest.getFileString("runtime_http.erl")).isPresent();
        assertThat(manifest.expectFileString("protocoljson_service_client.erl"))
                .contains("%% recording-erlang-integration was here");
    }

    @Test
    void clientDirectedCodegenOmitsWireModulesWhenServiceHasNoProtocolTrait() {
        Model model = loadModel("/model/dedicated_operation_io.smithy");
        MockManifest manifest = new MockManifest();
        PluginContext context = buildContext(
                model, manifest, "smithy.beam.demo.dedicated_io#DedicatedIoService");

        runClientDirectedCodegen(context);

        assertThat(manifest.getFileString("dedicated_io_service_rest_json_1.erl"))
                .isEmpty();
        assertThat(manifest.getFileString("runtime_http.erl")).isEmpty();
        assertThat(manifest.expectFileString("dedicated_io_service_client.erl"))
                .contains("%% recording-erlang-integration was here");
    }
}
