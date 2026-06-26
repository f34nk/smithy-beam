package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.ir.erlang.ErlCapturedBlock;
import io.smithy.beam.ir.erlang.ErlExpr;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.List;

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
        PaginationInfo pi = BeamClientPaginationSupport.requirePaginationInfo(ctx.model(), service, op);
        SymbolProvider sp = ctx.symbolProvider();
        Symbol opSym = sp.toSymbol(op);
        StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        String inputRecord = ErlangClientDispatchOperationIr.recordName(sp.toSymbol(input));
        String inputToken = ErlangClientDispatchOperationIr.fieldName(sp, pi.getInputTokenMember());
        String outputTokenExpr = ErlangClientDispatchOperationIr.buildRecordAccessExpr(
                        "Output", output, pi.getOutputTokenMemberPath(), ctx.model(), sp)
                .asString();
        List<MemberShape> itemsPath = pi.getItemsMemberPath();
        boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
        String itemsExpr = hasItems
                ? ErlangClientDispatchOperationIr.buildRecordAccessExpr(
                                "Output", output, itemsPath, ctx.model(), sp)
                        .asString()
                : null;

        writer.write("$L(Config, Input) ->", opSym.getName());
        writer.indent();
        writer.write("$L(Config, Input, []).", opSym.getName());
        writer.dedent();
        writer.write("");

        writer.write("$L(Config, Input, Acc) ->", opSym.getName());
        writer.indent();
        if (wrapWithRetry) {
            writer.write("RetryOpts = maps:get(retry, Config, #{}),");
            writer.write("case $L:with_retry(fun() ->", retryModule);
            writer.indent();
            emitPageDispatch.run();
            writer.dedent();
            writer.write("end, RetryOpts) of");
            writer.indent();
            writer.write("{ok, Output} ->");
            writer.indent();
            emitAccumulationAndRecursion(
                    writer, opSym, hasItems, itemsExpr, outputTokenExpr, inputRecord, inputToken);
            writer.dedent();
            writer.write("{error, Reason} ->");
            writer.indent();
            writer.write("{error, Reason}");
            writer.dedent();
            writer.write("end.");
        } else {
            emitPageDispatch.run();
        }
        writer.dedent();
    }

    static void emitAccumulationAndRecursion(
            ErlangWriter writer,
            Symbol opSym,
            boolean hasItems,
            String itemsExpr,
            String outputTokenExpr,
            String inputRecord,
            String inputToken) {
        ErlExpr items = hasItems ? ErlCapturedBlock.capturedBlock(itemsExpr) : null;
        ErlExpr outputToken = ErlCapturedBlock.capturedBlock(outputTokenExpr);
        ErlangClientDispatchIr.writeExprs(
                writer,
                ErlangClientDispatchOperationIr.buildAccumulationAndRecursion(
                        opSym, hasItems, items, outputToken, inputRecord, inputToken));
    }
}
