package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlModule;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangCodecEmission {
  private ErlangCodecEmission() {}

  static void writeModule(ErlangContext ctx, String relativeFile, ErlModule module) {
    ctx.writerDelegator()
        .useFileWriter(relativeFile, writer -> writer.write("$L", module.asString()));
  }

  static void emitRuntimeHelpersIfNeeded(ErlangContext ctx, ServiceShape service, boolean server) {
    if (server) {
      ErlangRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
    }
  }
}
