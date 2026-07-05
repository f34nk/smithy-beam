package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.MapEntry;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TypeAlias;
import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Index;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangClientIr {
  private ErlangClientIr() {}

  static Module clientModule(
      BeamErlangLayout layout,
      ServiceShape service,
      List<String> exports,
      List<Function> serviceFunctions,
      List<Function> operationFunctions) {
    List<String> headerComments = new ArrayList<>();
    headerComments.add("Generated Erlang client for " + service.getId() + ".");
    headerComments.add("Operation stubs use arity 2: (Config, Input).");
    headerComments.add("Service closure: " + service.getId());
    BeamAwsServiceMetadata.from(service)
        .ifPresent(
            meta -> {
              headerComments.add("AWS service metadata from model:");
              headerComments.add("  sdkId: " + meta.sdkId());
              headerComments.add("  endpointPrefix: " + meta.endpointPrefix());
            });

    List<Function> functions = new ArrayList<>(serviceFunctions);
    functions.addAll(operationFunctions);

    return Module.of(
        layout.clientModuleName(),
        functions,
        headerComments,
        null,
        List.of(layout.typesHeaderFile()),
        List.of(clientConfigTypeDef()),
        exports);
  }

  static TypeAlias clientConfigTypeDef() {
    return TypeAlias.of(
        "client_config",
        "#{binary() => term()}",
        List.of(
            "Client configuration is intentionally opaque at this layer; "
                + "endpoint, transport, and protocol live in future runtime modules."));
  }

  static List<Function> serviceFunctions(ServiceShape service, Model model, SymbolProvider sp) {
    List<Function> functions = new ArrayList<>();
    BeamAwsServiceMetadata.from(service)
        .ifPresent(meta -> functions.add(defaultConfigFunction(meta, model, sp, service)));
    return functions;
  }

  static Function defaultConfigFunction(
      BeamAwsServiceMetadata meta, Model model, SymbolProvider sp, ServiceShape service) {
    BeamSigV4Index sigv4Index = BeamSigV4Index.of(model, service);
    List<OperationShape> unsignedOps = sigv4Index.operationsWithUnsignedPayload(model, service);
    List<MapEntry> entries = new ArrayList<>();
    entries.add(MapEntry.of(AtomExpr.of("region"), BinaryExpr.of("us-east-1")));
    entries.add(
        MapEntry.of(AtomExpr.of("endpoint_prefix"), BinaryExpr.of(meta.endpointPrefix())));
    entries.add(MapEntry.of(AtomExpr.of("signing_name"), BinaryExpr.of(meta.signingName())));
    for (OperationShape op : unsignedOps) {
      Symbol opSym = sp.toSymbol(op);
      entries.add(
          MapEntry.of(
              TupleExpr.of(
                  List.of(AtomExpr.of("unsigned_payload"), AtomExpr.of(opSym.getName()))),
              AtomExpr.of("true")));
    }
    return Function.of(
        "default_config", List.of(FunctionClause.of(List.of(), MapExpr.of(entries))));
  }
}
