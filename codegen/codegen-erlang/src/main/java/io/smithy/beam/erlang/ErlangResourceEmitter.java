package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamResourceLifecycle;
import io.smithy.beam.ir.erlang.ErlModule;
import software.amazon.smithy.model.shapes.ResourceShape;

/** Generates per-resource lifecycle helper modules for client and server passes. */
public final class ErlangResourceEmitter {

  private ErlangResourceEmitter() {}

  public static void emitClient(ErlangContext ctx, ResourceShape resource) {
    if (!BeamResourceLifecycle.hasEmittableBindings(resource)) {
      return;
    }
    BeamResourceIndex index = BeamResourceIndex.of(ctx.model());
    BeamErlangLayout layout =
        new BeamErlangLayout(ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
    String resourceSnake = ctx.symbolProvider().toSymbol(resource).getName();
    String file = layout.resourceClientModuleFile(resourceSnake);
    ErlModule module =
        ErlangResourceIr.clientModule(ctx, resource, index, layout, layout.clientModuleName());
    ErlangCodecEmission.writeModule(ctx, file, module);
  }

  public static void emitServer(ErlangContext ctx, ResourceShape resource) {
    if (!BeamResourceLifecycle.hasEmittableBindings(resource)) {
      return;
    }
    BeamResourceIndex index = BeamResourceIndex.of(ctx.model());
    BeamErlangLayout layout =
        new BeamErlangLayout(ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
    String resourceSnake = ctx.symbolProvider().toSymbol(resource).getName();
    String file = layout.resourceServerModuleFile(resourceSnake);
    ErlModule module =
        ErlangResourceIr.serverModule(ctx, resource, index, layout, layout.serverModuleName());
    ErlangCodecEmission.writeModule(ctx, file, module);
  }
}
