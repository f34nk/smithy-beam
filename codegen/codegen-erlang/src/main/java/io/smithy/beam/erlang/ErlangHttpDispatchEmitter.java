package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.core.BeamSigV4Metadata;
import io.smithy.beam.ir.erlang.ErlAttribute;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlModule;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.List;

/**
 * Emits {@code runtime_http.erl} with an httpc-based HTTP dispatcher for generated clients.
 * The wrapper is thin: it converts http_request() to httpc args and wraps the response.
 */
public final class ErlangHttpDispatchEmitter {

    private ErlangHttpDispatchEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(),
                service.getId().getNamespace(), service);
        String httpModule = layout.runtimeHttpModuleName();
        boolean sigv4 = BeamSigV4Metadata.from(service).isPresent();
        boolean endpointRules = BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service);
        String endpointsMod = layout.endpointsModuleName();
        String credentialsMod = layout.credentialsModuleName();
        String helpersMod = layout.runtimeHelpersModuleName();
        String configVar = sigv4 ? "Config1" : "Config";

        ErlModule module = ErlangHttpDispatchIr.httpDispatchModule(
                httpModule,
                layout.runtimeTypesHeaderFile(),
                service,
                sigv4,
                endpointRules,
                configVar,
                helpersMod,
                endpointsMod,
                credentialsMod);
        ctx.writerDelegator().useFileWriter(layout.runtimeHttpModuleFile(), writer -> {
            writer.write("$L", module.asString());
        });
    }
}
