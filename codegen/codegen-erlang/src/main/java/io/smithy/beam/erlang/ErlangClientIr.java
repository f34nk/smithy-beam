package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Index;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAttribute;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlModuleAttribute;
import io.smithy.beam.ir.erlang.ErlPreambleEntry;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTypeDef;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangClientIr {
  private ErlangClientIr() {}

  static ErlModule clientModule(
      BeamErlangLayout layout,
      ServiceShape service,
      List<String> exports,
      List<ErlFunction> serviceFunctions,
      List<ErlFunction> operationFunctions) {
    List<ErlPreambleEntry> preamble = new ArrayList<>();
    preamble.add(ErlComment.comment("Generated Erlang client for " + service.getId() + "."));
    preamble.add(ErlComment.comment("Operation stubs use arity 2: (Config, Input)."));
    preamble.add(ErlComment.comment("Service closure: " + service.getId()));
    BeamAwsServiceMetadata.from(service)
        .ifPresent(
            meta -> {
              preamble.add(ErlComment.comment("AWS service metadata from model:"));
              preamble.add(ErlComment.comment("  sdkId: " + meta.sdkId()));
              preamble.add(ErlComment.comment("  endpointPrefix: " + meta.endpointPrefix()));
            });

    List<ErlModuleAttribute> attributes = new ArrayList<>();
    attributes.add(new ErlAttribute("include", "\"" + layout.typesHeaderFile() + "\""));
    attributes.add(ErlExportAttribute.export(exports));
    attributes.add(clientConfigTypeDef());

    List<ErlFunction> functions = new ArrayList<>(serviceFunctions);
    functions.addAll(operationFunctions);

    return new ErlModule(layout.clientModuleName(), preamble, attributes, functions);
  }

  static ErlTypeDef clientConfigTypeDef() {
    return new ErlTypeDef(
        "client_config",
        "#{binary() => term()}",
        List.of(
            ErlComment.comment(
                "Client configuration is intentionally opaque at this layer; "
                    + "endpoint, transport, and protocol live in future runtime modules.")));
  }

  static List<ErlFunction> serviceFunctions(ServiceShape service, Model model, SymbolProvider sp) {
    List<ErlFunction> functions = new ArrayList<>();
    BeamAwsServiceMetadata.from(service)
        .ifPresent(meta -> functions.add(defaultConfigFunction(meta, model, sp, service)));
    return functions;
  }

  static ErlFunction defaultConfigFunction(
      BeamAwsServiceMetadata meta, Model model, SymbolProvider sp, ServiceShape service) {
    BeamSigV4Index sigv4Index = BeamSigV4Index.of(model, service);
    List<OperationShape> unsignedOps = sigv4Index.operationsWithUnsignedPayload(model, service);
    List<ErlMapEntry> entries = new ArrayList<>();
    entries.add(ErlMapEntry.entry(ErlAtom.atom("region"), ErlBinary.binary("us-east-1")));
    entries.add(
        ErlMapEntry.entry(
            ErlAtom.atom("endpoint_prefix"), ErlBinary.binary(meta.endpointPrefix())));
    entries.add(
        ErlMapEntry.entry(ErlAtom.atom("signing_name"), ErlBinary.binary(meta.signingName())));
    for (OperationShape op : unsignedOps) {
      Symbol opSym = sp.toSymbol(op);
      entries.add(
          ErlMapEntry.entry(
              ErlTuple.tuple(ErlAtom.atom("unsigned_payload"), ErlAtom.atom(opSym.getName())),
              ErlAtom.atom("true")));
    }
    return ErlFunction.function(
        "default_config",
        0,
        List.of(ErlClause.clause(List.of(), ErlMap.map(entries.toArray(ErlMapEntry[]::new)))));
  }
}
