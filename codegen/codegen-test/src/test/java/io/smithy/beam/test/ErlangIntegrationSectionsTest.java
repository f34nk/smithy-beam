package io.smithy.beam.test;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.erlang.ErlangContext;
import io.smithy.beam.erlang.ErlangDirectedCodegen;
import io.smithy.beam.erlang.ErlangIntegration;
import io.smithy.beam.erlang.ErlangWriter;
import io.smithy.beam.test.support.RecordingErlangIntegration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;

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

    private static PluginContext buildContext(Model model, MockManifest manifest) {
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.basic#BasicService")
                .withMember("edition", "2026")
                .build();
        return PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build();
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

        ServiceShape serviceShape = context.getModel().expectShape(resolvedService, ServiceShape.class);
        if (settings.protocol() != null) {
            BeamProtocolResolver.resolve(context.getModel(), serviceShape, settings);
        }

        BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

        runner.run();

        assertThat(manifest.expectFileString(TYPES_FILE))
                .contains("%% recording-erlang-integration was here");
    }
}
