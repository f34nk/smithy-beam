package io.smithy.beam.elixir;

import io.beam.ir.elixir.Alias;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.Moduledoc;
import io.beam.ir.elixir.Module;
import io.smithy.beam.core.BeamElixirLayout;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirServerIr {
  private ElixirServerIr() {}

  static Module serverModule(
      BeamElixirLayout layout,
      ServiceShape service,
      String behaviourMod,
      String typesMod,
      List<Function> operationFunctions,
      List<Function> discoveryFunctions) {
    String serverMod = ElixirSymbolProvider.toModuleName(layout.serverModuleName());
    String implMod = ElixirSymbolProvider.toModuleName(layout.implModuleName());
    List<Function> functions = new ArrayList<>(operationFunctions);
    functions.addAll(discoveryFunctions);
    return new Module(
        serverMod,
        Moduledoc.of(
            "Generated Elixir server dispatcher for "
                + service.getId()
                + ".\n\nDiscovers impl callbacks at startup via init_handlers/0."),
        List.of(),
        List.of(
            Alias.of(typesMod, "Types"),
            Alias.of(behaviourMod, "Behaviour")),
        List.of(
            "@behaviour " + behaviourMod,
            "@default_impl " + implMod,
            "@handlers_key {" + serverMod + ", :handlers}",
            "# Call " + serverMod + ".init_handlers/0 during application start before dispatch.",
            "# Default impl module: " + implMod + "."),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }
}
