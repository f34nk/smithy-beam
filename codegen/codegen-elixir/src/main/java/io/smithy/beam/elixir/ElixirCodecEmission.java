package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExModule;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirCodecEmission {
  private ElixirCodecEmission() {}

  static void writeModule(ElixirContext ctx, String relativeFile, ExModule module) {
    ctx.writerDelegator()
        .useFileWriter(relativeFile, writer -> writer.write("$L", module.asString()));
  }

  static void emitRuntimeHelpersIfNeeded(ElixirContext ctx, ServiceShape service, boolean server) {
    if (server) {
      ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
    }
  }
}
