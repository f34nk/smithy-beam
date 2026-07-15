package io.smithy.beam.erlang;

import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.Module;
import io.smithy.beam.core.BeamErlangLayout;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangServerIr {
  private ErlangServerIr() {}

  static Module serverModule(
      BeamErlangLayout layout,
      ServiceShape service,
      String behaviourMod,
      List<String> exports,
      List<Function> operationFunctions,
      List<Function> discoveryFunctions) {
    List<Function> functions = new ArrayList<>(operationFunctions);
    functions.addAll(discoveryFunctions);

    return Module.server(
        layout.serverModuleName(),
        List.of(
            "Generated Erlang server dispatcher for " + service.getId() + ".",
            "Discovers impl callbacks at startup via init_handlers/0.",
            "Handler discovery and dispatch helpers."),
        behaviourMod,
        exports,
        layout.typesHeaderFile(),
        layout.implModuleName(),
        "{" + layout.serverModuleName() + ", handlers}",
        functions,
        List.of(
            "Call "
                + layout.serverModuleName()
                + ":init_handlers/0 during application start before dispatch.",
            "Default impl module: " + layout.implModuleName() + "."));
  }
}
