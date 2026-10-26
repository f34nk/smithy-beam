package io.smithy.beam.gleam;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamGleamStaticRuntimeEmitter;
import io.smithy.beam.core.BeamGleamStaticRuntimeIndex;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Runs Gleam type generation then client-specific DirectedCodegen on the same file manifest. */
public final class GleamClientGeneration {

  public void generate(PluginContext context) {
    new GleamTypeGeneration().generate(context);

    CodegenDirector<GleamWriter, GleamIntegration, GleamContext, BeamSettings> runner =
        new CodegenDirector<>();

    runner.directedCodegen(new GleamClientDirectedCodegen());
    runner.integrationClass(GleamIntegration.class);
    runner.fileManifest(context.getFileManifest());
    runner.integrationSettings(context.getSettings());
    context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);

    BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
    var resolvedService = settings.resolveService(context.getModel());
    runner.service(resolvedService);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings, context.getModel());

    runner.run();

    ServiceShape service = context.getModel().expectShape(resolvedService, ServiceShape.class);
    BeamGleamStaticRuntimeIndex.Requirements requirements =
        BeamGleamStaticRuntimeIndex.forClient(context.getModel(), service, settings);
    ClassLoader classLoader =
        context.getPluginClassLoader().orElseGet(GleamClientGeneration.class::getClassLoader);
    BeamGleamStaticRuntimeEmitter.emit(context.getFileManifest(), classLoader, requirements);
  }
}
