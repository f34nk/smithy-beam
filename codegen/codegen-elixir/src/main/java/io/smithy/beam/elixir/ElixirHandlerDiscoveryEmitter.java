package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;

/** Emits startup handler discovery and dispatch helpers into {@code {service}_server.ex}. */
final class ElixirHandlerDiscoveryEmitter {

  private ElixirHandlerDiscoveryEmitter() {}

  static void emitDiscoveryHelpers(ElixirContext ctx, BeamElixirLayout layout) {
    ElixirServerModuleBuilder builder = ctx.serverModuleBuilderOrNull();
    if (builder == null) {
      return;
    }
    builder.addDiscoveryFunctions(
        ElixirHandlerDiscoveryIr.discoveryFunctions(
            ElixirSymbolProvider.toModuleName(layout.behaviourModuleName())));
  }

  static void emitOperationDispatch(ElixirContext ctx, String handler) {
    ElixirServerModuleBuilder builder = ctx.serverModuleBuilderOrNull();
    if (builder == null) {
      return;
    }
    builder.addOperationFunction(ElixirHandlerDiscoveryIr.operationDispatch(handler));
  }
}
