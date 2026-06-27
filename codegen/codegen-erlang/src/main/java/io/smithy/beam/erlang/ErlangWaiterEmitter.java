package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamWaiterIndex;
import io.smithy.beam.ir.erlang.ErlModule;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Generates a {@code <service>_waiters.erl} helper for {@code @waitable} operations. */
public final class ErlangWaiterEmitter {

  private ErlangWaiterEmitter() {}

  public static void emit(ErlangContext ctx, ServiceShape service) {
    BeamWaiterIndex index = BeamWaiterIndex.of(ctx.model(), service);
    if (index.isEmpty()) {
      return;
    }

    BeamErlangLayout layout =
        new BeamErlangLayout(ctx.settings(), service.getId().getNamespace(), service);

    ctx.writerDelegator()
        .useFileWriter(
            layout.waitersModuleFile(),
            writer -> {
              ErlModule module =
                  ErlangWaiterIr.waitersModule(
                      layout.waitersModuleName(),
                      layout.typesHeaderFile(),
                      layout.clientModuleName(),
                      index,
                      ctx.symbolProvider(),
                      ctx.model(),
                      service);
              writer.write("$L", module.asString());
            });
  }
}
