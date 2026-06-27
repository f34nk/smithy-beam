package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;

/**
 * Runs Elixir type generation through {@link CodegenDirector} and {@link ElixirDirectedCodegen}.
 */
public final class ElixirTypeGeneration {

  public void generate(PluginContext context) {
    CodegenDirector<ElixirWriter, ElixirIntegration, ElixirContext, BeamSettings> runner =
        new CodegenDirector<>();

    runner.directedCodegen(new ElixirDirectedCodegen());
    runner.integrationClass(ElixirIntegration.class);
    runner.fileManifest(context.getFileManifest());
    runner.integrationSettings(context.getSettings());
    context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);
    runner.model(context.getModel());

    BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
    var resolvedService = settings.resolveService(context.getModel());
    runner.service(resolvedService);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings);

    runner.run();
  }
}
