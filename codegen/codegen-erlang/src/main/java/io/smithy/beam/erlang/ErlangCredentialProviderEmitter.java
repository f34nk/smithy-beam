package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import io.smithy.beam.ir.erlang.ErlModule;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits {@code <service>_credentials.erl} with a default AWS credential resolution chain
 * for services with {@code @aws.auth#sigv4}.
 */
public final class ErlangCredentialProviderEmitter {

    private ErlangCredentialProviderEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        if (BeamSigV4Metadata.from(service).isEmpty()) {
            return;
        }

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String credentialsModule = layout.credentialsModuleName();

        ctx.writerDelegator().useFileWriter(layout.credentialsModuleFile(), writer -> {
            ErlModule module = ErlangCredentialProviderIr.credentialsModule(credentialsModule, service);
            writer.write("$L", module.asString());
        });
    }
}
