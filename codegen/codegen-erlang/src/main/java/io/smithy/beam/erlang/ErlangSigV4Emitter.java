package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits a self-contained {@code <service>_sigv4.erl} signing module for services with
 * {@code @aws.auth#sigv4}. Callers may supply credentials in client config or
 * rely on the generated credential provider module.
 */
public final class ErlangSigV4Emitter {

    private ErlangSigV4Emitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        if (BeamSigV4Metadata.from(service).isEmpty()) {
            return;
        }

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String sigv4Module = layout.sigv4ModuleName();

        ctx.writerDelegator().useFileWriter(layout.sigv4ModuleFile(), writer -> writer.write(
                "$L",
                ErlangSigV4Ir.sigV4Module(
                                sigv4Module, layout.runtimeTypesHeaderFile(), service)
                        .asString()));
    }
}
