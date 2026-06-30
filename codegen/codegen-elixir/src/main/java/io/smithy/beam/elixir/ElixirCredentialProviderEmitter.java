package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import io.smithy.beam.ir.elixir.ExModule;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits {@code <service>_credentials.ex} with a default AWS credential resolution chain for
 * services with {@code @aws.auth#sigv4}.
 */
public final class ElixirCredentialProviderEmitter {

  private ElixirCredentialProviderEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    if (BeamSigV4Metadata.from(service).isEmpty()) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ExModule module = ElixirCredentialProviderIr.credentialsModule(ctx, service);

    ctx.writerDelegator()
        .useFileWriter(
            layout.credentialsModuleFile(),
            writer -> writer.write("$L", module.asString()));
  }
}
