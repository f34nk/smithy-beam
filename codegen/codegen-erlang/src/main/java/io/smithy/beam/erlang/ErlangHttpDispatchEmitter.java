package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.model.shapes.ServiceShape;

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

        ctx.writerDelegator().useFileWriter(layout.runtimeHttpModuleFile(), writer -> {
            writer.write("%% Generated HTTP dispatcher for $L.", service.getId());
            writer.write("%% Uses httpc from OTP. Replace via adapter for testing.");
            writer.write("-module($L).", httpModule);
            writer.write("-include(\"$L\").", layout.runtimeTypesHeaderFile());
            writer.write("-export([dispatch/2, dispatch/3]).");
            writer.write("");
            writer.write("%% @doc Sends an http_request() and returns http_response().");
            writer.write("%% Config may contain `{base_url, ...}` and `{http_client, Module}` for tests.");
            writer.write("%% Uses httpc by default; pass another module for tests.");
            ErlangHttpDispatchIr.writeFunction(writer, ErlangHttpDispatchIr.dispatchArity2());
            ErlangHttpDispatchIr.writeFunction(writer, ErlangHttpDispatchIr.dispatchArity3());
            ErlangHttpDispatchIr.writeFunction(writer, ErlangHttpDispatchIr.dispatchSigned(
                    sigv4, endpointRules, configVar, helpersMod, endpointsMod, credentialsMod));
            ErlangHttpDispatchIr.writeFunction(writer, ErlangHttpDispatchIr.splitBaseUrl());
            ErlangHttpDispatchIr.writeFunction(writer, ErlangHttpDispatchIr.mime());
        });
    }
}
