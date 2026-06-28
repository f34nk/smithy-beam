package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExModule;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code runtime_http.ex} with a Req-based HTTP dispatcher for generated clients. */
public final class ElixirHttpDispatchEmitter {

  private ElixirHttpDispatchEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ExModule module = ElixirHttpDispatchIr.httpDispatchModule(ctx, service);

    ctx.writerDelegator()
        .useFileWriter(
            layout.runtimeHttpModuleFile(), writer -> writer.write("$L", module.asString()));
  }
}
