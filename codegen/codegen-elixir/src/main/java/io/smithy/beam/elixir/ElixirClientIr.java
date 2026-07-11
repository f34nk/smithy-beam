package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExBlankLine;
import io.smithy.beam.ir.elixir.ExComment;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExPreambleEntry;
import io.smithy.beam.ir.elixir.ExTypeDef;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirClientIr {
  private ElixirClientIr() {}

  static ExModule clientModule(
      BeamElixirLayout layout,
      ServiceShape service,
      String typesModuleName,
      List<ExFunction> operationFunctions) {
    List<ExPreambleEntry> preamble = new ArrayList<>();
    preamble.add(
        ExModuledoc.moduledoc(
            "Generated Elixir client for "
                + service.getId()
                + ".\n\nOperation stubs accept config and input. "
                + "Transport and protocol are not generated here."));
    BeamAwsServiceMetadata.from(service)
        .ifPresent(
            meta -> {
              preamble.add(ExComment.comment("AWS service metadata from model:"));
              preamble.add(ExComment.comment("  sdkId: " + meta.sdkId()));
              preamble.add(ExComment.comment("  endpointPrefix: " + meta.endpointPrefix()));
            });

    List<ExModuleEntry> entries = new ArrayList<>();
    entries.add(clientConfigTypeDef());
    if (!operationFunctions.isEmpty()) {
      entries.add(new ExBlankLine());
    }
    entries.addAll(operationFunctions);

    return ExModule.module(
        ElixirSymbolProvider.toModuleName(layout.clientModuleName()),
        preamble,
        List.of(ExAliasAttr.alias(typesModuleName, "Types")),
        List.of(),
        List.of(),
        entries);
  }

  static ExTypeDef clientConfigTypeDef() {
    return ExTypeDef.alias(
        "client_config",
        "map()",
        List.of(
            ExComment.comment(
                "Client configuration is intentionally opaque at this layer; "
                    + "endpoint, transport, and protocol live in future runtime modules.")));
  }
}
