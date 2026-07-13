package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExModule;

final class ElixirCodecEmission {
  private ElixirCodecEmission() {}

  static void writeModule(ElixirContext ctx, String relativeFile, ExModule module) {
    ctx.writerDelegator()
        .useFileWriter(relativeFile, writer -> writer.write("$L", module.asString()));
  }
}
