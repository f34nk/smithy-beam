package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;

/**
 * Runs Erlang type generation through {@link CodegenDirector} and {@link
 * ErlangTypeDirectedCodegen}. Call from {@link ErlangTypesPlugin} or from other generators in this
 * module that need the same type output.
 */
public final class ErlangTypeGeneration {

  /**
   * Performs directed codegen for the Erlang types generator using the given Smithy-Build plugin
   * context.
   */
  public void generate(PluginContext context) {
    CodegenDirector<ErlangWriter, ErlangIntegration, ErlangContext, BeamSettings> runner =
        new CodegenDirector<>();

    runner.directedCodegen(new ErlangTypeDirectedCodegen());
    runner.integrationClass(ErlangIntegration.class);
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
