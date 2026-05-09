package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;

/**
 * Runs Erlang type generation then server-specific DirectedCodegen on the same
 * file manifest.
 */
public final class ErlangServerGeneration {

    /**
     * Runs types then server codegen using the given Smithy-Build plugin context.
     */
    public void generate(PluginContext context) {
        new ErlangTypeGeneration().generate(context);

        CodegenDirector<ErlangWriter, ErlangIntegration, ErlangContext, BeamSettings> runner =
                new CodegenDirector<>();

        runner.directedCodegen(new ErlangServerDirectedCodegen());
        runner.integrationClass(ErlangIntegration.class);
        runner.fileManifest(context.getFileManifest());
        runner.model(context.getModel());

        BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
        runner.service(settings.resolveService(context.getModel()));

        runner.performDefaultCodegenTransforms();
        runner.createDedicatedInputsAndOutputs();

        runner.run();
    }
}
