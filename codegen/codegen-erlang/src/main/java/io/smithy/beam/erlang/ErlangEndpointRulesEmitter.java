package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamContextParamsIndex;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.core.BeamErlangLayout;
import java.util.Map;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code <service>_endpoints.erl} when the model defines {@code @endpointRuleSet}. */
public final class ErlangEndpointRulesEmitter {

  private ErlangEndpointRulesEmitter() {}

  public static void emit(ErlangContext ctx, ServiceShape service) {
    if (!BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service)) {
      return;
    }

    BeamErlangLayout layout =
        new BeamErlangLayout(ctx.settings(), service.getId().getNamespace(), service);
    String endpointsModule = layout.endpointsModuleName();
    Map<String, String> clientContextKeys = BeamContextParamsIndex.clientContextConfigKeys(service);

    ErlangCodecEmission.writeModule(
        ctx,
        layout.endpointsModuleFile(),
        ErlangEndpointRulesIr.endpointRulesModule(
            endpointsModule, layout.runtimeTypesHeaderFile(), service, clientContextKeys));
  }
}
