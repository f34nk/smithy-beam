package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Module;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamWaiterIndex;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Generates a {@code <Service>Waiters} helper for {@code @waitable} operations. */
public final class ElixirWaiterEmitter {

  private ElixirWaiterEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    BeamWaiterIndex index = BeamWaiterIndex.of(ctx.model(), service);
    if (index.isEmpty()) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    Module module =
        ElixirWaiterDsl.waitersModule(ctx, service, index, ctx.symbolProvider(), ctx.model());
    ElixirCodecEmission.writeModule(ctx, layout.waitersModuleFile(), module);
  }
}
