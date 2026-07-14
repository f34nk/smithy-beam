package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExExpr;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;

final class ElixirClientDispatchIr {
  private ElixirClientDispatchIr() {}

  static List<ExExpr> operationBodyExprs(
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

  static String renderBody(List<ExExpr> exprs) {
    List<String> lines = new ArrayList<>();
    for (ExExpr expr : exprs) {
      lines.addAll(expr.lines());
    }
    return String.join("\n", lines);
  }
}
