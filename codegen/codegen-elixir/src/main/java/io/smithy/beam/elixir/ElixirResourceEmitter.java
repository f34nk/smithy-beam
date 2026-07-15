package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamResourceLifecycle;
import software.amazon.smithy.model.shapes.ResourceShape;

/** Generates per-resource lifecycle helper modules for client and server passes. */
public final class ElixirResourceEmitter {

  private ElixirResourceEmitter() {}

  public static void emitClient(ElixirContext ctx, ResourceShape resource) {
    if (!BeamResourceLifecycle.hasEmittableBindings(resource)) {
      return;
    }
    BeamResourceIndex index = BeamResourceIndex.of(ctx.model());
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
    String resourceSnake = ctx.symbolProvider().toSymbol(resource).getName();
    String file = layout.resourceClientModuleFile(resourceSnake);
    String delegateMod = ElixirSymbolProvider.toModuleName(layout.clientModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    ElixirCodecEmission.writeModule(
        ctx,
        file,
        ElixirResourceDsl.clientModule(ctx, resource, index, layout, delegateMod, typesMod));
  }

  public static void emitServer(ElixirContext ctx, ResourceShape resource) {
    if (!BeamResourceLifecycle.hasEmittableBindings(resource)) {
      return;
    }
    BeamResourceIndex index = BeamResourceIndex.of(ctx.model());
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
    String resourceSnake = ctx.symbolProvider().toSymbol(resource).getName();
    String file = layout.resourceServerModuleFile(resourceSnake);
    String delegateMod = ElixirSymbolProvider.toModuleName(layout.serverModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    ElixirCodecEmission.writeModule(
        ctx,
        file,
        ElixirResourceDsl.serverModule(ctx, resource, index, layout, delegateMod, typesMod));
  }
}
