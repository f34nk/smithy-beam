package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamElixirStaticRuntimeEmitter;
import io.smithy.beam.core.BeamElixirStaticRuntimeIndex;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Runs Elixir type generation then server-specific DirectedCodegen on the same file manifest. */
public final class ElixirServerGeneration {

  /** Runs types then server codegen using the given Smithy-Build plugin context. */
  public void generate(PluginContext context) {
    new ElixirTypeGeneration().generate(context);

    CodegenDirector<ElixirWriter, ElixirIntegration, ElixirContext, BeamSettings> runner =
        new CodegenDirector<>();

    runner.directedCodegen(new ElixirServerDirectedCodegen());
    runner.integrationClass(ElixirIntegration.class);
    runner.fileManifest(context.getFileManifest());
    runner.integrationSettings(context.getSettings());
    context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);

    BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
    var resolvedService = settings.resolveService(context.getModel());
    runner.service(resolvedService);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings, context.getModel());

    runner.run();

    ServiceShape service = context.getModel().expectShape(resolvedService, ServiceShape.class);
    BeamElixirStaticRuntimeIndex.Requirements requirements =
        BeamElixirStaticRuntimeIndex.forServer(context.getModel(), service, settings);
    ClassLoader classLoader =
        context.getPluginClassLoader().orElseGet(ElixirServerGeneration.class::getClassLoader);
    BeamElixirStaticRuntimeEmitter.emit(context.getFileManifest(), classLoader, requirements);
  }
}
