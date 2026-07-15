package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamProtocolSupport;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Generates a service-scoped router module that matches incoming HTTP requests to server handler
 * functions using the @http trait bindings.
 */
public final class ErlangRouterEmitter {

  private ErlangRouterEmitter() {}

  public static void emit(ErlangContext ctx, ServiceShape service) {
    if (!BeamProtocolSupport.hasWireCodegen(
        ctx.resolvedProtocolTraitId(), ctx.protocolCodegen(), ctx.integrations())) {
      return;
    }
    BeamErlangLayout layout =
        new BeamErlangLayout(ctx.settings(), service.getId().getNamespace(), service);
    List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(ctx.model(), service);
    ErlangCodecEmission.writeModule(
        ctx,
        layout.routerModuleFile(),
        ErlangRouterDsl.routerModule(
            ctx.model(),
            service,
            layout,
            ctx.resolvedProtocolTraitId(),
            operations,
            ctx.symbolProvider()));
  }
}
