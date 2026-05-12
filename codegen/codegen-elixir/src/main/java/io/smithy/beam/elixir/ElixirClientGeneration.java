package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;

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
        runner.service(settings.resolveService(context.getModel()));

        BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

        runner.run();
    }
}
