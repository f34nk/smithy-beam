package io.smithy.beam.elixir;

import io.beam.ir.elixir.AtomExpr;
import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Expression;
import io.beam.ir.elixir.MapEntry;
import io.beam.ir.elixir.StringExpr;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExString;
import java.util.List;

final class ElixirBeamIrBridge {
  private ElixirBeamIrBridge() {}

  static ExExpr expr(Expression expression) {
    return ExCapturedBlock.capturedBlock(ElixirRenderer.renderExpression(expression));
  }

  static ExExpr statement(Expression expression) {
    return ExCapturedBlock.capturedBlock(ElixirRenderer.renderStatement(expression));
  }

  static ExMapEntry mapEntry(MapEntry entry) {
    if (entry.key() instanceof AtomExpr atom) {
      return ExMapEntry.entry(ExAtom.atom(atom.value()), expr(entry.value()));
    }
    if (entry.key() instanceof StringExpr str) {
      return ExMapEntry.entry(ExString.string(str.value()), expr(entry.value()));
    }
    return ExMapEntry.entry(expr(entry.key()), expr(entry.value()));
  }

  static List<ExMapEntry> mapEntries(List<MapEntry> entries) {
    return entries.stream().map(ElixirBeamIrBridge::mapEntry).toList();
  }

  static ExExpr rejectNilMapPipeline(String bindingVar, List<ExMapEntry> entries) {
    return io.smithy.beam.ir.elixir.ExPipeline.pipeline(
        bindingVar,
        new io.smithy.beam.ir.elixir.ExMap(entries),
        io.smithy.beam.ir.elixir.ExCall.call(
            "Enum",
            "reject",
            io.smithy.beam.ir.elixir.ExAnonymousFn.compactFn(
                io.smithy.beam.ir.elixir.ExClause.inlineClause(
                    List.of(
                        io.smithy.beam.ir.elixir.ExTuplePattern.tuple(
                            io.smithy.beam.ir.elixir.ExVarPattern.var("_"),
                            io.smithy.beam.ir.elixir.ExVarPattern.var("v"))),
                    io.smithy.beam.ir.elixir.ExCall.call(
                        "Kernel", "is_nil", io.smithy.beam.ir.elixir.ExVar.var("v"))))),
        io.smithy.beam.ir.elixir.ExCall.call("Map", "new"));
  }
}
