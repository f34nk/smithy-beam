package io.smithy.beam.gleam;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;

/**
 * Runs Gleam type generation through {@link CodegenDirector} and {@link GleamTypeDirectedCodegen}.
 */
public final class GleamTypeGeneration {

  public void generate(PluginContext context) {
    CodegenDirector<GleamWriter, GleamIntegration, GleamContext, BeamSettings> runner =
        new CodegenDirector<>();

    runner.directedCodegen(new GleamTypeDirectedCodegen());
    runner.integrationClass(GleamIntegration.class);
    runner.fileManifest(context.getFileManifest());
    runner.integrationSettings(context.getSettings());
    context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);

    BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
    var resolvedService = settings.resolveService(context.getModel());
    runner.service(resolvedService);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings, context.getModel());

    runner.run();
  }
}
