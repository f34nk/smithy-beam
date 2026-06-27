package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlFunction;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Emits pagination loops inline in generated client operations for {@code @paginated} operations.
 */
public final class ErlangClientPaginationEmitter {

  private ErlangClientPaginationEmitter() {}

  public static void emitPaginatedOperation(
      ErlangContext ctx,
      ServiceShape service,
      OperationShape op,
      boolean wrapWithRetry,
      String retryModule,
      Runnable emitPageDispatch,
      ErlangWriter writer) {
    Symbol opSym = ctx.symbolProvider().toSymbol(op);
    StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
    StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
    Symbol inSym = ctx.symbolProvider().toSymbol(input);
    Symbol outSym = ctx.symbolProvider().toSymbol(output);
    BeamErlangLayout layout =
        new BeamErlangLayout(ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
    var paginationInfo =
        BeamClientPaginationSupport.requirePaginationInfo(ctx.model(), service, op);
    String successReturnType;
    if (BeamClientPaginationSupport.hasItemsMember(paginationInfo)) {
      successReturnType =
          "["
              + BeamClientPaginationSupport.itemsElementSymbol(
                      ctx.model(), ctx.symbolProvider(), paginationInfo)
                  .orElseThrow()
                  .getName()
              + "]";
    } else {
      successReturnType = "[" + outSym.getName() + "]";
    }

    for (ErlFunction fn :
        ErlangClientPaginationIr.paginatedOperationFunctions(
            ctx, service, op, layout, wrapWithRetry, retryModule, successReturnType, null)) {
      writer.write("$L", fn.asString());
      writer.write("$L", "");
    }
  }
}
