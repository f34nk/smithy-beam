package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExCallbackSpec;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExPreambleEntry;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExTuple;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirBehaviourIr {
  private ElixirBehaviourIr() {}

  static ExModule behaviourModule(
      BeamElixirLayout layout,
      ServiceShape service,
      List<ExCallbackSpec> callbacks,
      List<OperationShape> operations,
      SymbolProvider sp) {
    String behaviourMod = ElixirSymbolProvider.toModuleName(layout.behaviourModuleName());
    String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<ExPreambleEntry> preamble =
        List.of(
            ExModuledoc.moduledoc(
                "Generated Elixir server behaviour for " + service.getId() + "."));
    return ExModule.module(
        behaviourMod,
        preamble,
        List.of(ExAliasAttr.alias(typesModuleName, "Types")),
        callbacks,
        List.of(callbacksFunction(operations, sp)));
  }

  static ExCallbackSpec operationCallback(
      ElixirContext ctx, OperationShape op, SymbolProvider sp) {
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
    ExDoc doc = BeamDocumentation.forShape(op).map(ExDoc::doc).orElse(null);
    return ExCallbackSpec.callbackSpec(
        handler,
        List.of("term()", inType, "term()"),
        "{:ok, " + outType + "} | {:error, term()}",
        doc);
  }

  static ExFunction callbacksFunction(List<OperationShape> operations, SymbolProvider sp) {
    List<ExExpr> entries = new ArrayList<>();
    for (OperationShape op : operations) {
      String name = sp.toSymbol(op).getName();
      entries.add(
          ExTuple.tuple(ExAtom.atom("handle_" + name), ExInteger.integer(3)));
    }
    return ExFunction.functionWithSpec(
        "def",
        "callbacks",
        ExSpec.functionSpec("callbacks", "", "[{atom(), non_neg_integer()}]"),
        List.of(ExClause.blockClause(List.of(), ExList.list(entries.toArray(ExExpr[]::new)))));
  }
}
