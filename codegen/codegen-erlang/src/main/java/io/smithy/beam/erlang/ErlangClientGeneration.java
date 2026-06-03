package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamDependencyManifestEmitter;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs Erlang type generation then client-specific DirectedCodegen on the same
 * file manifest.
 */
public final class ErlangClientGeneration {

    /**
     * Runs types then client codegen using the given Smithy-Build plugin context.
     */
    public void generate(PluginContext context) {
        new ErlangTypeGeneration().generate(context);

        CodegenDirector<ErlangWriter, ErlangIntegration, ErlangContext, BeamSettings> runner =
                new CodegenDirector<>();

        AtomicReference<ErlangContext> clientContext = new AtomicReference<>();
        runner.directedCodegen(
                BeamDependencyManifestEmitter.capturingContext(
                        new ErlangClientDirectedCodegen(), clientContext));
        runner.integrationClass(ErlangIntegration.class);
        runner.fileManifest(context.getFileManifest());
        runner.integrationSettings(context.getSettings());
        context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);
        runner.model(context.getModel());

        BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
        var serviceId = settings.resolveService(context.getModel());
        runner.service(serviceId);

        BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

        runner.run();

        ServiceShape service = context.getModel().expectShape(serviceId, ServiceShape.class);
        BeamDependencyManifestEmitter.emit(
                context.getFileManifest(),
                clientContext.get().writerDelegator(),
                settings,
                context.getModel(),
                service);
    }
}
