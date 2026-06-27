package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code <service>_presigner.erl} with presigned URL helpers for SigV4 services. */
public final class ErlangPresignerEmitter {

  private ErlangPresignerEmitter() {}

  public static void emit(ErlangContext ctx, ServiceShape service) {
    if (BeamSigV4Metadata.from(service).isEmpty()) {
      return;
    }

    BeamErlangLayout layout =
        new BeamErlangLayout(ctx.settings(), service.getId().getNamespace(), service);
    String presignerModule = layout.presignerModuleName();
    String sigv4Module = layout.sigv4ModuleName();

    ctx.writerDelegator()
        .useFileWriter(
            layout.presignerModuleFile(),
            writer -> {
              writer.write(
                  "$L",
                  ErlangPresignerIr.presignerModule(
                          presignerModule, layout.runtimeTypesHeaderFile(), sigv4Module, service)
                      .asString());
            });
  }
}
