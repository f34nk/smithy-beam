package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.ir.elixir.ExModule;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code <service>_endpoints.ex} when the model defines {@code @endpointRuleSet}. */
public final class ElixirEndpointRulesEmitter {

  private ElixirEndpointRulesEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    if (!BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service)) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ExModule module = ElixirEndpointRulesIr.endpointRulesModule(ctx, service);

    ctx.writerDelegator()
        .useFileWriter(
            layout.endpointsModuleFile(), writer -> writer.write("$L", module.asString()));
  }
}
