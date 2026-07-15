package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Module;
import io.smithy.beam.core.BeamS3CustomizationIndex;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code S3Endpoint} bucket virtual-host and path-style helpers for S3 REST-XML clients. */
public final class ElixirS3EndpointEmitter {

  private ElixirS3EndpointEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    if (!BeamS3CustomizationIndex.isS3Service(service)) {
      return;
    }

    Module module = ElixirS3EndpointDsl.s3EndpointModule(service);
    ElixirCodecEmission.writeModule(ctx, "s3_endpoint.ex", module);
  }
}
