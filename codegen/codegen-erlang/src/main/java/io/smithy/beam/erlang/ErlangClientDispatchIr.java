package io.smithy.beam.erlang;

import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Expression;
import io.smithy.beam.core.BeamErlangLayout;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;

final class ErlangClientDispatchIr {
  private ErlangClientDispatchIr() {}

  static List<Expression> operationBodyExprs(
      ErlangContext ctx,
      OperationShape op,
      BeamErlangLayout layout,
      boolean wrapWithRetry,
      boolean paginated,
      ErlangClientDispatchOperationIr.DispatchBodyMode mode) {
    return ErlangClientDispatchOperationIr.buildDispatchBody(
        ctx, op, layout, wrapWithRetry, paginated, mode);
  }

  /** Test helper; delete once ErlangClientDispatchIrTest renders via ErlangRenderer directly. */
  static void writeExprs(ErlangWriter writer, List<Expression> exprs) {
    if (exprs.isEmpty()) {
      return;
    }
    Expression block = exprs.size() == 1 ? exprs.get(0) : BlockExpr.commaSeparated(exprs, false);
    writer.write("$L", ErlangRenderer.renderStatement(block));
  }
}
