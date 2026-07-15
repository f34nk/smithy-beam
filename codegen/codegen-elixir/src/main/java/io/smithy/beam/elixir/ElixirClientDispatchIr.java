package io.smithy.beam.elixir;

import io.beam.ir.elixir.BlockExpr;
import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Expression;
import io.smithy.beam.core.BeamElixirLayout;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;

final class ElixirClientDispatchIr {
  private ElixirClientDispatchIr() {}

  static List<Expression> operationBodyExprs(
      ElixirContext ctx,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String clientModule,
      boolean paginated,
      ElixirClientDispatchOperationIr.DispatchBodyMode mode) {
    return ElixirClientDispatchOperationIr.buildDispatchBody(
        ctx, op, layout, wrapWithRetry, clientModule, paginated, mode);
  }

  /** Test helper; prefer ElixirRenderer.renderStatement / renderExpression in tests. */
  static String renderBody(List<Expression> exprs) {
    Expression block = exprs.size() == 1 ? exprs.get(0) : new BlockExpr(exprs);
    return ElixirRenderer.renderStatement(block);
  }
}
