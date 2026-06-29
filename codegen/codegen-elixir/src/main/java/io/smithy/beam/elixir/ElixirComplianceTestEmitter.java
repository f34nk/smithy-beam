package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExModule;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code test/<service>_compliance_tests.ex} from HTTP protocol compliance traits. */
public final class ElixirComplianceTestEmitter {

  private ElixirComplianceTestEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    ExModule module = ElixirComplianceTestIr.complianceTestsModule(ctx, service);
    if (module == null) {
      return;
    }
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ElixirCodecEmission.writeModule(ctx, layout.complianceTestsModuleFile(), module);
  }
}
