package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Generates {@code {service}_behaviour.erl} with one {@code -callback} per operation and {@code
 * behaviour_info/1} listing expected handler callbacks.
 */
final class ErlangBehaviourEmitter {

  private ErlangBehaviourEmitter() {}

  static void beginService(
      ErlangContext ctx, ServiceShape service, List<OperationShape> operations) {
    // Callbacks accumulate in context; module is written in finishService.
  }

  static void emitOperationCallback(ErlangContext ctx, OperationShape op, SymbolProvider sp) {
    ErlangBehaviourModuleBuilder builder = ctx.behaviourModuleBuilderOrNull();
    if (builder == null) {
      return;
    }
    builder.addCallback(ErlangBehaviourIr.operationCallback(ctx, op, sp));
  }

  static void finishService(
      ErlangContext ctx, ServiceShape service, List<OperationShape> operations, SymbolProvider sp) {
    ErlangBehaviourModuleBuilder builder = ctx.behaviourModuleBuilderOrNull();
    if (builder == null) {
      return;
    }
    BeamErlangLayout layout =
        new BeamErlangLayout(ctx.settings(), service.getId().getNamespace(), service);
    ErlangCodecEmission.writeModule(
        ctx,
        layout.behaviourModuleFile(),
        ErlangBehaviourIr.behaviourModule(layout, service, builder.callbacks()));
  }
}
