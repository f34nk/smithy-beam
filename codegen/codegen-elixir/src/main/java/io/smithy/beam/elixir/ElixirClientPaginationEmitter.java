package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExFunction;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

public final class ElixirClientPaginationEmitter {

  private ElixirClientPaginationEmitter() {}

  public static List<ExFunction> paginatedOperationFunctions(
      ElixirContext ctx,
      ServiceShape service,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String retryModule,
      String successReturnType,
      ExDoc docOrNull) {
    return ElixirClientPaginationIr.paginatedOperationFunctions(
        ctx, service, op, layout, wrapWithRetry, retryModule, successReturnType, docOrNull);
  }
}
