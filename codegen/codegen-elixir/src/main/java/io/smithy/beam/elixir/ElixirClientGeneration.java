package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Runs Elixir type generation then client-specific DirectedCodegen on the same
 * file manifest.
 */
public final class ElixirClientGeneration {

    /**
     * Runs types then client codegen using the given Smithy-Build plugin context.
     */
    public void generate(PluginContext context) {
        new ElixirTypeGeneration().generate(context);

        CodegenDirector<ElixirWriter, ElixirIntegration, ElixirContext, BeamSettings> runner =
                new CodegenDirector<>();

        runner.directedCodegen(new ElixirClientDirectedCodegen());
        runner.integrationClass(ElixirIntegration.class);
        runner.fileManifest(context.getFileManifest());
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
    }
}
