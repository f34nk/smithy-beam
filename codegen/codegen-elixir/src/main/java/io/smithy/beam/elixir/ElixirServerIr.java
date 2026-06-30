package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExBehaviourAttr;
import io.smithy.beam.ir.elixir.ExComment;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuleAssignAttr;
import io.smithy.beam.ir.elixir.ExModuleAttribute;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExPreambleEntry;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExVar;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirServerIr {
  private ElixirServerIr() {}

  static ExModule serverModule(
      BeamElixirLayout layout,
      ServiceShape service,
      String behaviourMod,
      String typesMod,
      List<ExFunction> operationFunctions,
      List<ExFunction> discoveryFunctions) {
    String serverMod = ElixirSymbolProvider.toModuleName(layout.serverModuleName());
    String implMod = ElixirSymbolProvider.toModuleName(layout.implModuleName());
    List<ExPreambleEntry> preamble =
        List.of(
            ExModuledoc.moduledoc(
                "Generated Elixir server dispatcher for "
                    + service.getId()
                    + ".\n\nDiscovers impl callbacks at startup via init_handlers/0."));
    List<ExModuleAttribute> attributes =
        List.of(
            ExBehaviourAttr.behaviour(behaviourMod),
            ExAliasAttr.alias(typesMod, "Types"),
            ExAliasAttr.alias(behaviourMod, "Behaviour"),
            ExModuleAssignAttr.assign("default_impl", ExVar.var(implMod)),
            ExModuleAssignAttr.assign(
                "handlers_key", ExTuple.tuple(ExVar.var(serverMod), ExAtom.atom("handlers"))));
    List<ExFunction> functions = new ArrayList<>(operationFunctions);
    functions.addAll(discoveryFunctions);
    List<ExModuleEntry> epilogue =
        List.of(
            ExComment.comment(
                "Call " + serverMod + ".init_handlers/0 during application start before dispatch."),
            ExComment.comment("Default impl module: " + implMod + "."));
    return ExModule.module(serverMod, preamble, attributes, List.of(), functions, epilogue);
  }
}
