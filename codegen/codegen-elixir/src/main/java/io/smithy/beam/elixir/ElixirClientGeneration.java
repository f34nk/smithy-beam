package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamElixirStaticRuntimeEmitter;
import io.smithy.beam.core.BeamElixirStaticRuntimeIndex;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Runs Elixir type generation then client-specific DirectedCodegen on the same file manifest. */
public final class ElixirClientGeneration {

  /** Runs types then client codegen using the given Smithy-Build plugin context. */
  public void generate(PluginContext context) {
    new ElixirTypeGeneration().generate(context);

    CodegenDirector<ElixirWriter, ElixirIntegration, ElixirContext, BeamSettings> runner =
        new CodegenDirector<>();

    runner.directedCodegen(new ElixirClientDirectedCodegen());
    runner.integrationClass(ElixirIntegration.class);
    runner.fileManifest(context.getFileManifest());
    runner.integrationSettings(context.getSettings());
    context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);

    BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
    var serviceId = settings.resolveService(context.getModel());
    runner.service(serviceId);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings, context.getModel());

    runner.run();

    ServiceShape service = context.getModel().expectShape(serviceId, ServiceShape.class);
    BeamElixirStaticRuntimeIndex.Requirements requirements =
        BeamElixirStaticRuntimeIndex.forClient(context.getModel(), service, settings);
    ClassLoader classLoader =
        context.getPluginClassLoader().orElseGet(ElixirClientGeneration.class::getClassLoader);
    BeamElixirStaticRuntimeEmitter.emit(context.getFileManifest(), classLoader, requirements);
  }
}
