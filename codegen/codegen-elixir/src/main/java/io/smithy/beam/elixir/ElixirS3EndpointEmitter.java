package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.ir.elixir.ExModule;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code S3Endpoint} bucket virtual-host and path-style helpers for S3 REST-XML clients. */
public final class ElixirS3EndpointEmitter {

  private ElixirS3EndpointEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    if (!BeamS3CustomizationIndex.isS3Service(service)) {
      return;
    }

    ExModule module = ElixirS3EndpointIr.s3EndpointModule(service);
    ctx.writerDelegator()
        .useFileWriter("s3_endpoint.ex", writer -> writer.write("$L", module.asString()));
  }
}
