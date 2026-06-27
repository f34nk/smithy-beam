package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlAttribute;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlModuleAttribute;
import io.smithy.beam.ir.erlang.ErlPreambleEntry;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangServerIr {
  private ErlangServerIr() {}

  static ErlModule serverModule(
      BeamErlangLayout layout,
      ServiceShape service,
      String behaviourMod,
      List<String> exports,
      List<ErlFunction> operationFunctions,
      List<ErlFunction> discoveryFunctions) {
    List<ErlPreambleEntry> preamble = new ArrayList<>();
    preamble.add(
        ErlComment.comment("Generated Erlang server dispatcher for " + service.getId() + "."));
    preamble.add(ErlComment.comment("Discovers impl callbacks at startup via init_handlers/0."));
    preamble.add(ErlComment.comment("Handler discovery and dispatch helpers."));

    List<ErlModuleAttribute> attributes = new ArrayList<>();
    attributes.add(new ErlAttribute("behaviour", behaviourMod));
    attributes.add(ErlExportAttribute.export(exports));
    attributes.add(new ErlAttribute("include", "\"" + layout.typesHeaderFile() + "\""));
    attributes.add(new ErlAttribute("define", "DEFAULT_IMPL, " + layout.implModuleName()));
    attributes.add(
        new ErlAttribute("define", "HANDLERS_KEY, {" + layout.serverModuleName() + ", handlers}"));

    List<ErlFunction> functions = new ArrayList<>(operationFunctions);
    functions.addAll(discoveryFunctions);

    List<ErlPreambleEntry> epilogue =
        List.of(
            ErlComment.comment(
                "Call "
                    + layout.serverModuleName()
                    + ":init_handlers/0 during application start before dispatch."),
            ErlComment.comment("Default impl module: " + layout.implModuleName() + "."));

    return new ErlModule(layout.serverModuleName(), preamble, attributes, functions, epilogue);
  }
}
