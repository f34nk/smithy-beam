package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenTransforms;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.core.BeamStaticRuntimeEmitter;
import io.smithy.beam.core.BeamStaticRuntimeIndex;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Runs Erlang type generation then server-specific DirectedCodegen on the same file manifest. */
public final class ErlangServerGeneration {

  /** Runs types then server codegen using the given Smithy-Build plugin context. */
  public void generate(PluginContext context) {
    new ErlangTypeGeneration().generate(context);

    CodegenDirector<ErlangWriter, ErlangIntegration, ErlangContext, BeamSettings> runner =
        new CodegenDirector<>();

    runner.directedCodegen(new ErlangServerDirectedCodegen());
    runner.integrationClass(ErlangIntegration.class);
    runner.fileManifest(context.getFileManifest());
    runner.integrationSettings(context.getSettings());
    context.getPluginClassLoader().ifPresent(runner::integrationClassLoader);

    BeamSettings settings = runner.settings(BeamSettings.class, context.getSettings());
    var serviceId = settings.resolveService(context.getModel());
    runner.service(serviceId);

    BeamCodegenTransforms.applySharedCodegenTransforms(runner, settings, context.getModel());

    runner.run();

    ServiceShape service = context.getModel().expectShape(serviceId, ServiceShape.class);
    BeamStaticRuntimeIndex.Requirements requirements =
        BeamStaticRuntimeIndex.forServer(context.getModel(), service, settings);
    ClassLoader classLoader =
        context.getPluginClassLoader().orElseGet(ErlangServerGeneration.class::getClassLoader);
    BeamStaticRuntimeEmitter.emit(context.getFileManifest(), classLoader, requirements);
  }
}
