package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import io.smithy.beam.ir.elixir.ExModule;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits a self-contained {@code <service>_sigv4.ex} signing module for services with
 * {@code @aws.auth#sigv4}. Callers may supply credentials in client config or rely on the generated
 * credential provider module.
 */
public final class ElixirSigV4Emitter {

  private ElixirSigV4Emitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    if (BeamSigV4Metadata.from(service).isEmpty()) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ExModule module = ElixirSigV4Ir.sigV4Module(ctx, service);
    ctx.writerDelegator()
        .useFileWriter(
            layout.sigv4ModuleFile(), writer -> writer.write("$L", module.asString()));
  }
}
