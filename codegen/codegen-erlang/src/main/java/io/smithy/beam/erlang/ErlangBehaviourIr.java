package io.smithy.beam.erlang;

import io.beam.ir.erlang.Callback;
import io.beam.ir.erlang.Edoc;
import io.beam.ir.erlang.FunctionDoc;
import io.beam.ir.erlang.Module;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamErlangLayout;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ErlangBehaviourIr {
  private ErlangBehaviourIr() {}

  static Module behaviourModule(
      BeamErlangLayout layout, ServiceShape service, List<Callback> callbacks) {
    return Module.behaviour(
        layout.behaviourModuleName(),
        List.of("Generated Erlang server behaviour for " + service.getId() + "."),
        layout.typesHeaderFile(),
        callbacks);
  }

  static Callback operationCallback(ErlangContext ctx, OperationShape op, SymbolProvider sp) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
    StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
    Symbol inSym = sp.toSymbol(input);
    Symbol outSym = sp.toSymbol(output);
    String name = "handle_" + opSym.getName();
    String inputTypes = "Ctx :: term(), Input :: " + inSym.getName() + ", Meta :: term()";
    String outputTypes = "{ok, " + outSym.getName() + "} | {error, term()}";
    FunctionDoc doc =
        BeamDocumentation.forShape(op).map(Edoc::of).orElse(null);
    return Callback.of(name, inputTypes, outputTypes, doc);
  }
}
