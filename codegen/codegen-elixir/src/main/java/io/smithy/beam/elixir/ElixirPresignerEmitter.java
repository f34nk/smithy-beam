package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import io.smithy.beam.ir.elixir.ExModule;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code <service>_presigner.ex} with presigned URL helpers for SigV4 services. */
public final class ElixirPresignerEmitter {

  private ElixirPresignerEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    if (BeamSigV4Metadata.from(service).isEmpty()) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ExModule module = ElixirPresignerIr.presignerModule(ctx, service, layout.sigv4ModuleName());
    ctx.writerDelegator()
        .useFileWriter(
            layout.presignerModuleFile(), writer -> writer.write("$L", module.asString()));
  }
}
