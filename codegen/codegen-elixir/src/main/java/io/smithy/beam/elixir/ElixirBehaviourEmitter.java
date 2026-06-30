package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExModule;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Generates {@code {service}_behaviour.ex} with one {@code @callback} per operation and {@code
 * callbacks/0} listing expected handler callbacks.
 */
final class ElixirBehaviourEmitter {

  private ElixirBehaviourEmitter() {}

  static void beginService(
      ElixirContext ctx, ServiceShape service, List<OperationShape> operations) {
    // Callbacks accumulate in context; module is written in finishService.
  }

  static void emitOperationCallback(ElixirContext ctx, OperationShape op, SymbolProvider sp) {
    ElixirBehaviourModuleBuilder builder = ctx.behaviourModuleBuilderOrNull();
    if (builder == null) {
      return;
    }
    builder.addCallback(ElixirBehaviourIr.operationCallback(ctx, op, sp));
  }

  static void finishService(ElixirContext ctx, List<OperationShape> operations, SymbolProvider sp) {
    ElixirBehaviourModuleBuilder builder = ctx.behaviourModuleBuilderOrNull();
    if (builder == null) {
      return;
    }
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
    ExModule module =
        ElixirBehaviourIr.behaviourModule(
            layout, ctx.service(), builder.callbacks(), operations, sp);
    ElixirCodecEmission.writeModule(ctx, layout.behaviourModuleFile(), module);
  }
}
