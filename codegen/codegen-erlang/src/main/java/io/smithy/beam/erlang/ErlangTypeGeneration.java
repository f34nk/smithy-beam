package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;

/**
 * Runs Erlang type generation through {@link CodegenDirector} and
 * {@link ErlangDirectedCodegen}. Call from {@link ErlangTypesPlugin} or from
 * other generators in this module that need the same type output.
 */
public final class ErlangTypeGeneration {

    /**
     * Performs directed codegen for the Erlang types generator using the given
     * Smithy-Build plugin context.
     */
    public void generate(PluginContext context) {
        CodegenDirector<ErlangWriter, ErlangIntegration, ErlangContext, BeamSettings> runner = new CodegenDirector<>();

        runner.directedCodegen(new ErlangDirectedCodegen());
        runner.integrationClass(ErlangIntegration.class);
        runner.fileManifest(context.getFileManifest());
        runner.integrationSettings(context.getSettings());
        context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);
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
