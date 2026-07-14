package io.smithy.beam.elixir;

import io.beam.ir.elixir.Alias;
import io.beam.ir.elixir.AtomExpr;
import io.beam.ir.elixir.Callback;
import io.beam.ir.elixir.Expression;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.FunctionDoc;
import io.beam.ir.elixir.FunctionHead;
import io.beam.ir.elixir.IntegerExpr;
import io.beam.ir.elixir.ListExpr;
import io.beam.ir.elixir.Moduledoc;
import io.beam.ir.elixir.Module;
import io.beam.ir.elixir.Spec;
import io.beam.ir.elixir.TupleExpr;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamElixirLayout;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirBehaviourIr {
  private ElixirBehaviourIr() {}

  static Module behaviourModule(
      BeamElixirLayout layout,
      ServiceShape service,
      List<Callback> callbacks,
      List<OperationShape> operations,
      SymbolProvider sp) {
    String behaviourMod = ElixirSymbolProvider.toModuleName(layout.behaviourModuleName());
    String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    return new Module(
        behaviourMod,
        Moduledoc.of("Generated Elixir server behaviour for " + service.getId() + "."),
        List.of(),
        List.of(Alias.of(typesModuleName, "Types")),
        List.of(),
        List.of(),
        callbacks,
        List.of(),
        List.of(callbacksFunction(operations, sp)));
  }

  static Callback operationCallback(ElixirContext ctx, OperationShape op, SymbolProvider sp) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
    StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
    String handler = "handle_" + opSym.getName();
    String typesModuleName =
        ElixirSymbolProvider.toModuleName(
            new BeamElixirLayout(
                    ctx.settings(), ctx.service().getId().getNamespace(), ctx.service())
                .typesModuleName());
    String inType = ElixirTopDown.structureSpecType(typesModuleName, sp.toSymbol(input));
    String outType = ElixirTopDown.structureSpecType(typesModuleName, sp.toSymbol(output));
    FunctionDoc doc = BeamDocumentation.forShape(op).map(FunctionDoc::of).orElse(null);
    return Callback.of(
        handler,
        List.of("term()", inType, "term()"),
        "{:ok, " + outType + "} | {:error, term()}",
        doc);
  }

  static Function callbacksFunction(List<OperationShape> operations, SymbolProvider sp) {
    List<Expression> entries = new ArrayList<>();
    for (OperationShape op : operations) {
      String name = sp.toSymbol(op).getName();
      entries.add(TupleExpr.of(List.of(AtomExpr.of("handle_" + name), IntegerExpr.of(3))));
    }
    return new Function(
        "callbacks",
        false,
        List.of(FunctionHead.of(List.of())),
        ListExpr.of(entries),
        Spec.of("@spec callbacks() :: [{atom(), non_neg_integer()}]"),
        null,
        false);
  }
}
