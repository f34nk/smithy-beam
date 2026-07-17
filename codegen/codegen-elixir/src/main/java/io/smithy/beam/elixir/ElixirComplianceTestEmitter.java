package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Module;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code test/<service>_compliance_test.ex} from HTTP protocol compliance traits. */
public final class ElixirComplianceTestEmitter {

  private ElixirComplianceTestEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service, BeamCodegenKind kind) {
    Module module = ElixirComplianceTestDsl.complianceTestsModule(ctx, service, kind);
    if (module == null) {
      return;
    }
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ElixirCodecEmission.writeModule(ctx, layout.complianceTestsModuleFile(), module);
  }
}
