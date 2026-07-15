package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;

/** Contributes handler discovery helpers into the server {@link io.beam.dsl.erlang.Module}. */
final class ErlangHandlerDiscoveryEmitter {

  private ErlangHandlerDiscoveryEmitter() {}

  static void emitDiscoveryHelpers(ErlangContext ctx, BeamErlangLayout layout) {
    ErlangServerModuleBuilder builder = ctx.serverModuleBuilderOrNull();
    if (builder == null) {
      return;
    }
    builder.addDiscoveryFunctions(
        ErlangHandlerDiscoveryIr.discoveryFunctions(layout.behaviourModuleName()));
  }

  static void emitOperationDispatch(ErlangContext ctx, String handler) {
    ErlangServerModuleBuilder builder = ctx.serverModuleBuilderOrNull();
    if (builder == null) {
      return;
    }
    builder.addOperationFunction(ErlangHandlerDiscoveryIr.operationDispatch(handler));
  }
}
