package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamClientPaginationSupport;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
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
        String inputRecord = recordName(sp.toSymbol(input));
        String inputToken = fieldName(sp, pi.getInputTokenMember());
        String outputTokenExpr = recordAccess(
                "Output", output, pi.getOutputTokenMemberPath(), ctx.model(), sp);
        List<MemberShape> itemsPath = pi.getItemsMemberPath();
        boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
        String itemsExpr = hasItems
                ? recordAccess("Output", output, itemsPath, ctx.model(), sp)
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
        if (hasItems) {
            writer.write("NewAcc = Acc ++ $L,", itemsExpr);
        } else {
            writer.write("NewAcc = [Output | Acc],");
        }
        writer.write("case $L of", outputTokenExpr);
        writer.indent();
        if (hasItems) {
            writer.write("undefined ->");
            writer.indent();
            writer.write("{ok, NewAcc};");
            writer.dedent();
        } else {
            writer.write("undefined -> {ok, lists:reverse(NewAcc)};");
        }
        writer.write("NextToken ->");
        writer.indent();
        writer.write("NextInput = Input#$L{$L = NextToken},", inputRecord, inputToken);
        writer.write("$L(Config, NextInput, NewAcc)", opSym.getName());
        writer.dedent();
        writer.dedent();
        writer.write("end;");
    }

    static String recordName(Symbol symbol) {
        return symbol.getName().replace("()", "");
    }

    static String fieldName(SymbolProvider sp, MemberShape member) {
        return sp.toSymbol(member).getProperty("fieldName", String.class).orElseThrow();
    }

    static String recordAccess(
            String rootVar,
            StructureShape rootShape,
            List<MemberShape> path,
            software.amazon.smithy.model.Model model,
            SymbolProvider sp) {
        String expr = rootVar;
        Shape container = rootShape;
        for (MemberShape member : path) {
            String record = recordName(sp.toSymbol(container));
            String field = fieldName(sp, member);
            expr = "element(#" + record + "." + field + ", " + expr + ")";
            container = model.expectShape(member.getTarget(), Shape.class);
        }
        return expr;
    }
}
