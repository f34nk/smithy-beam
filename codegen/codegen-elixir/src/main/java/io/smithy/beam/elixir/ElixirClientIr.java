package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSigV4Index;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExBlankLine;
import io.smithy.beam.ir.elixir.ExComment;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExPreambleEntry;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTypeDef;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirClientIr {
  private ElixirClientIr() {}

  static ExModule clientModule(
      BeamElixirLayout layout,
      ServiceShape service,
      String typesModuleName,
      List<ExFunction> serviceFunctions,
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
    entries.add(new ExBlankLine());
    entries.addAll(serviceFunctions);
    if (!serviceFunctions.isEmpty() && !operationFunctions.isEmpty()) {
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

  static List<ExFunction> serviceFunctions(
      ServiceShape service, Model model, SymbolProvider sp) {
    List<ExFunction> functions = new ArrayList<>();
    BeamAwsServiceMetadata.from(service)
        .ifPresent(meta -> functions.add(defaultConfigFunction(meta, model, sp, service)));
    return functions;
  }

  static ExFunction defaultConfigFunction(
      BeamAwsServiceMetadata meta, Model model, SymbolProvider sp, ServiceShape service) {
    BeamSigV4Index sigv4Index = BeamSigV4Index.of(model, service);
    List<OperationShape> unsignedOps = sigv4Index.operationsWithUnsignedPayload(model, service);
    List<ExMapEntry> entries = new ArrayList<>();
    entries.add(ExMapEntry.entry(ExAtom.atom("region"), ExString.string("us-east-1")));
    entries.add(
        ExMapEntry.entry(
            ExAtom.atom("endpoint_prefix"), ExString.string(meta.endpointPrefix())));
    entries.add(
        ExMapEntry.entry(ExAtom.atom("signing_name"), ExString.string(meta.signingName())));
    for (OperationShape op : unsignedOps) {
      Symbol opSym = sp.toSymbol(op);
      entries.add(
          ExMapEntry.entry(
              ExTuple.tuple(ExAtom.atom("unsigned_payload"), ExAtom.atom(opSym.getName())),
              ExAtom.atom("true")));
    }
    return ExFunction.functionWithSpec(
        "def",
        "default_config",
        ExSpec.functionSpec("default_config", "()", "map()"),
        List.of(ExClause.blockClause(List.of(), ExMap.map(entries.toArray(ExMapEntry[]::new)))));
  }
}
