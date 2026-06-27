package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFun;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlFunctionDoc;
import io.smithy.beam.ir.erlang.ErlFunctionSpec;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlPattern;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.List;

final class ErlangClientPaginationIr {
    private ErlangClientPaginationIr() {}

    static List<ErlFunction> paginatedOperationFunctions(
            ErlangContext ctx,
            ServiceShape service,
            OperationShape op,
            BeamErlangLayout layout,
            boolean wrapWithRetry,
            String retryModule,
            String successReturnType,
            ErlFunctionDoc docOrNull) {
        SymbolProvider sp = ctx.symbolProvider();
        Symbol opSym = sp.toSymbol(op);
        StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        Symbol inSym = sp.toSymbol(input);
        String opName = opSym.getName();
        String specOutput = "{'ok', " + successReturnType + "} | {'error', term()}";

        ErlFunction arity2 = new ErlFunction(
                opName,
                2,
                docOrNull,
                ErlFunctionSpec.functionSpec(opName, "client_config(), " + inSym.getName(), specOutput),
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Config"), ErlVarPattern.varPattern("Input")),
                        ErlCallLocal.callLocal(
                                opName,
                                ErlVar.var("Config"),
                                ErlVar.var("Input"),
                                ErlList.list()))));

        List<ErlExpr> pageBody = ErlangClientDispatchIr.operationBodyExprs(
                ctx,
                op,
                layout,
                wrapWithRetry,
                retryModule,
                true,
                ErlangClientDispatchOperationIr.DispatchBodyMode.PAGINATED_PAGE);

        List<ErlClause> arity3Clauses = List.of(paginatedArity3Clause(
                ctx, service, op, wrapWithRetry, retryModule, pageBody, sp, opSym));

        ErlFunction arity3 = new ErlFunction(
                opName,
                3,
                null,
                ErlFunctionSpec.functionSpec(
                        opName,
                        "client_config(), " + inSym.getName() + ", " + successReturnType,
                        specOutput),
                arity3Clauses);

        return List.of(arity2, arity3);
    }

    private static ErlClause paginatedArity3Clause(
            ErlangContext ctx,
            ServiceShape service,
            OperationShape op,
            boolean wrapWithRetry,
            String retryModule,
            List<ErlExpr> pageBody,
            SymbolProvider sp,
            Symbol opSym) {
        List<ErlPattern> patterns = List.of(
                ErlVarPattern.varPattern("Config"),
                ErlVarPattern.varPattern("Input"),
                ErlVarPattern.varPattern("Acc"));
        if (wrapWithRetry) {
            return ErlClause.blockClause(
                    patterns,
                    ErlExprBlock.block(
                            retryWrappedPageBody(ctx, service, op, retryModule, pageBody, sp, opSym)
                                    .toArray(ErlExpr[]::new)));
        }
        return ErlClause.blockClause(patterns, ErlExprBlock.block(pageBody.toArray(ErlExpr[]::new)));
    }

    private static List<ErlExpr> retryWrappedPageBody(
            ErlangContext ctx,
            ServiceShape service,
            OperationShape op,
            String retryModule,
            List<ErlExpr> pageBody,
            SymbolProvider sp,
            Symbol opSym) {
        PaginationInfo pi = BeamClientPaginationSupport.requirePaginationInfo(ctx.model(), service, op);
        StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        String inputRecord = ErlangClientDispatchOperationIr.recordName(sp.toSymbol(input));
        String inputToken = ErlangClientDispatchOperationIr.fieldName(sp, pi.getInputTokenMember());
        ErlExpr outputTokenExpr = ErlangClientDispatchOperationIr.buildRecordAccessExpr(
                "Output", output, pi.getOutputTokenMemberPath(), ctx.model(), sp);
        boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
        ErlExpr itemsExpr = hasItems
                ? ErlangClientDispatchOperationIr.buildRecordAccessExpr(
                        "Output", output, pi.getItemsMemberPath(), ctx.model(), sp)
                : null;

        List<ErlExpr> body = new ArrayList<>();
        body.add(ErlMatch.match(
                ErlVarPattern.varPattern("RetryOpts"),
                ErlCall.call("maps", "get", ErlAtom.atom("retry"), ErlVar.var("Config"), ErlMap.map())));
        ErlFun pageFun = ErlFun.fun(ErlClause.blockClause(List.of(), ErlExprBlock.block(pageBody.toArray(ErlExpr[]::new))));
        ErlCase retryCase = ErlCase.caseExpr(
                ErlCall.call(retryModule, "with_retry", pageFun, ErlVar.var("RetryOpts")),
                ErlClause.blockClause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("ok"), ErlVarPattern.varPattern("Output"))),
                        ErlExprBlock.block(
                                ErlangClientDispatchOperationIr.buildAccumulationAndRecursion(
                                                opSym,
                                                hasItems,
                                                itemsExpr,
                                                outputTokenExpr,
                                                inputRecord,
                                                inputToken)
                                        .toArray(ErlExpr[]::new))),
                ErlClause.blockClause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("error"), ErlVarPattern.varPattern("Reason"))),
                        ErlTuple.tuple(ErlAtom.atom("error"), ErlVar.var("Reason"))));
        body.add(retryCase);
        return body;
    }
}
