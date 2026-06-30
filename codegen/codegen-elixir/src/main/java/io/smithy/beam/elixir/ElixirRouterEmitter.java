package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamProtocolSupport;
import io.smithy.beam.ir.elixir.ExModule;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Generates {@code <App>Router}: a dispatch module that routes HTTP requests to server handlers.
 */
public final class ElixirRouterEmitter {

  private ElixirRouterEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    if (!BeamProtocolSupport.hasWireCodegen(
        ctx.resolvedProtocolTraitId(), ctx.protocolCodegen(), ctx.integrations())) {
      return;
    }
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ExModule module =
        ElixirRouterIr.routerModule(
            ctx.model(),
            service,
            layout,
            ctx.resolvedProtocolTraitId(),
            ElixirTopDown.containedOperationsSorted(ctx.model(), service),
            ctx.symbolProvider());
    ElixirCodecEmission.writeModule(ctx, layout.routerModuleFile(), module);
  }
}
