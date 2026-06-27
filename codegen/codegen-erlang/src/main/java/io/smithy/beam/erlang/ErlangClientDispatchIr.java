package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlExpr;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;

final class ErlangClientDispatchIr {
  private ErlangClientDispatchIr() {}

  static List<ErlExpr> operationBodyExprs(
      ErlangContext ctx,
      OperationShape op,
      BeamErlangLayout layout,
      boolean wrapWithRetry,
      String retryModule,
      boolean paginated,
      ErlangClientDispatchOperationIr.DispatchBodyMode mode) {
    return ErlangClientDispatchOperationIr.buildDispatchBody(
        ctx, op, layout, wrapWithRetry, retryModule, paginated, mode);
  }

  static void writeExprs(ErlangWriter writer, List<ErlExpr> exprs) {
    for (int i = 0; i < exprs.size(); i++) {
      boolean isLast = i == exprs.size() - 1;
      List<String> lines = exprs.get(i).lines();
      for (int j = 0; j < lines.size(); j++) {
        String line = lines.get(j);
        if (j == lines.size() - 1) {
          if (isLast) {
            line = line + ".";
          } else {
            line = line + ",";
          }
        }
        writer.write("$L", line);
      }
    }
  }
}
