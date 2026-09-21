package io.smithy.beam.elixir;

import io.beam.lang.elixir.BlockExpr;
import io.beam.lang.elixir.ElixirRenderer;
import io.beam.lang.elixir.Expression;
import io.smithy.beam.core.BeamElixirLayout;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;

final class ElixirClientDispatchDsl {
  private ElixirClientDispatchDsl() {}

  static List<Expression> operationBodyExprs(
      ElixirContext ctx,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String clientModule,
      boolean paginated,
      ElixirClientDispatchOperationDsl.DispatchBodyMode mode) {
    return ElixirClientDispatchOperationDsl.buildDispatchBody(
        ctx, op, layout, wrapWithRetry, clientModule, paginated, mode);
  }

  /** Test helper; prefer ElixirRenderer.renderStatement / renderExpression in tests. */
  static String renderBody(List<Expression> exprs) {
    Expression block = exprs.size() == 1 ? exprs.get(0) : BlockExpr.of(exprs);
    return ElixirRenderer.renderStatement(block);
  }
}
