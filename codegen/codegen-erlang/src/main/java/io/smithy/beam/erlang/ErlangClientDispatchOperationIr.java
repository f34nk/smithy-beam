package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCapturedBlock;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.List;

final class ErlangClientDispatchOperationIr {
    private ErlangClientDispatchOperationIr() {}

    enum DispatchBodyMode {
        SINGLE_PAGE,
        PAGINATED_PAGE
    }

    private record DispatchContext(
            ErlangContext ctx,
            OperationShape op,
            BeamErlangLayout layout,
            boolean wrapWithRetry,
            String retryModule,
            boolean paginated,
            DispatchBodyMode mode,
            Symbol opSym,
            String opName,
            String codecModule,
            String runtimeHttpModule,
            String sigv4Module,
            boolean sigv4,
            boolean encodeWithConfig) {}

    private enum CaseTerminator {
        NONE(""),
        STATEMENT("."),
        CLAUSE(";");

        private final String suffix;

        CaseTerminator(String suffix) {
            this.suffix = suffix;
        }
    }

    static List<ErlExpr> buildDispatchBody(
            ErlangContext ctx,
            OperationShape op,
            BeamErlangLayout layout,
            boolean wrapWithRetry,
            String retryModule,
            boolean paginated,
            DispatchBodyMode mode) {
        DispatchContext dispatch = buildContext(ctx, op, layout, wrapWithRetry, retryModule, paginated, mode);
        List<ErlExpr> core = new ArrayList<>();
        core.add(buildEncodeRequestMatch(dispatch));
        if (dispatch.sigv4()) {
            core.add(buildSignedRequestMatch(dispatch));
        }
        core.add(buildDispatchCase(dispatch));
        if (dispatch.mode() == DispatchBodyMode.SINGLE_PAGE && dispatch.wrapWithRetry()) {
            return buildRetryWrappedBody(dispatch, core);
        }
        return core;
    }

    static List<ErlExpr> buildAccumulationAndRecursion(
            Symbol opSym,
            boolean hasItems,
            ErlExpr itemsExpr,
            ErlExpr outputTokenExpr,
            String inputRecord,
            String inputToken) {
        List<ErlExpr> body = new ArrayList<>();
        if (hasItems) {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("NewAcc"),
                    ErlOp.op("++", ErlVar.var("Acc"), itemsExpr)));
        } else {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("NewAcc"),
                    ErlList.cons(ErlVar.var("Output"), ErlVar.var("Acc"))));
        }
        ErlExpr undefinedSuccess = hasItems
                ? ErlTuple.tuple(ErlAtom.atom("ok"), ErlVar.var("NewAcc"))
                : ErlTuple.tuple(ErlAtom.atom("ok"), ErlCall.call("lists", "reverse", ErlVar.var("NewAcc")));
        ErlExpr nextInput = ErlCapturedBlock.capturedBlock(
                "Input#" + inputRecord + "{" + inputToken + " = NextToken}");
        ErlExpr recurse = ErlCallLocal.callLocal(
                opSym.getName(),
                ErlVar.var("Config"),
                ErlVar.var("NextInput"),
                ErlVar.var("NewAcc"));
        ErlClause undefinedClause = hasItems
                ? ErlClause.blockClause(
                        List.of(ErlAtomPattern.atomPattern("undefined")), undefinedSuccess)
                : ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), undefinedSuccess);
        ErlCase tokenCase = ErlCase.caseExpr(
                outputTokenExpr,
                undefinedClause,
                ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("NextToken")),
                        ErlExprBlock.block(
                                ErlMatch.match(ErlVarPattern.varPattern("NextInput"), nextInput),
                                recurse)));
        body.add(tokenCase);
        return body;
    }

    static ErlExpr buildRecordAccessExpr(
            String rootVar,
            StructureShape rootShape,
            List<MemberShape> path,
            Model model,
            SymbolProvider sp) {
        ErlExpr expr = ErlVar.var(rootVar);
        Shape container = rootShape;
        for (MemberShape member : path) {
            String record = recordName(sp.toSymbol(container));
            String field = fieldName(sp, member);
            expr = ErlCallLocal.callLocal(
                    "element",
                    ErlCapturedBlock.capturedBlock("#" + record + "." + field),
                    expr);
            container = model.expectShape(member.getTarget(), Shape.class);
        }
        return expr;
    }

    static String recordName(Symbol symbol) {
        return symbol.getName().replace("()", "");
    }

    static String fieldName(SymbolProvider sp, MemberShape member) {
        return sp.toSymbol(member).getProperty("fieldName", String.class).orElseThrow();
    }

    private static DispatchContext buildContext(
            ErlangContext ctx,
            OperationShape op,
            BeamErlangLayout layout,
            boolean wrapWithRetry,
            String retryModule,
            boolean paginated,
            DispatchBodyMode mode) {
        SymbolProvider sp = ctx.symbolProvider();
        Symbol opSym = sp.toSymbol(op);
        boolean sigv4 = BeamSigV4Metadata.from(ctx.service()).isPresent();
        boolean encodeWithConfig = ErlangRestJsonSupport.serviceHasHostLabelOperations(ctx.model(), ctx.service())
                || ErlangRestXmlSupport.serviceEncodesWithConfig(ctx.model(), ctx.service());
        return new DispatchContext(
                ctx,
                op,
                layout,
                wrapWithRetry,
                retryModule,
                paginated,
                mode,
                opSym,
                opSym.getName(),
                layout.clientCodecModuleName(ctx.resolvedProtocolTraitId(), ctx.integrations()),
                layout.runtimeHttpModuleName(),
                layout.sigv4ModuleName(),
                sigv4,
                encodeWithConfig);
    }

    private static ErlMatch buildEncodeRequestMatch(DispatchContext ctx) {
        ErlExpr encodeCall = ctx.encodeWithConfig()
                ? ErlCall.call(
                        ctx.codecModule(),
                        "encode_" + ctx.opName() + "_request",
                        ErlVar.var("Config"),
                        ErlVar.var("Input"))
                : ErlCall.call(ctx.codecModule(), "encode_" + ctx.opName() + "_request", ErlVar.var("Input"));
        return ErlMatch.match(ErlVarPattern.varPattern("Req"), encodeCall);
    }

    private static ErlMatch buildSignedRequestMatch(DispatchContext ctx) {
        ErlCase credentialsCase = ErlCase.caseExpr(
                ErlCall.call(
                        "maps",
                        "get",
                        ErlAtom.atom("credentials"),
                        ErlVar.var("Config"),
                        ErlAtom.atom("undefined")),
                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlVar.var("Req")),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("_")),
                        ErlCall.call(
                                ctx.sigv4Module(),
                                "sign",
                                ErlVar.var("Config"),
                                ErlAtom.atom(ctx.opName()),
                                ErlVar.var("Req"))));
        return ErlMatch.match(ErlVarPattern.varPattern("SignedReq"), credentialsCase);
    }

    private static ErlExpr dispatchRequestVar(DispatchContext ctx) {
        return ctx.sigv4() ? ErlVar.var("SignedReq") : ErlVar.var("Req");
    }

    private static ErlExpr buildDispatchCase(DispatchContext ctx) {
        ErlExpr successExpr = buildDecodeSuccessExpr(ctx);
        ErlCase dispatchCase = ErlCase.caseExpr(
                ErlCall.call(
                        ctx.runtimeHttpModule(),
                        "dispatch",
                        ErlVar.var("Config"),
                        dispatchRequestVar(ctx)),
                ErlClause.blockClause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("ok"),
                                ErlVarPattern.varPattern("Resp"))),
                        successExpr),
                ErlClause.blockClause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("error"),
                                ErlVarPattern.varPattern("Reason"))),
                        ErlTuple.tuple(ErlAtom.atom("error"), ErlVar.var("Reason"))));
        return suffixCase(dispatchCase, caseTerminator(ctx));
    }

    private static ErlExpr buildDecodeSuccessExpr(DispatchContext ctx) {
        ErlExpr decode = ErlCall.call(
                ctx.codecModule(),
                "decode_" + ctx.opName() + "_response",
                ErlVar.var("Resp"));
        if (ctx.mode() == DispatchBodyMode.SINGLE_PAGE) {
            return decode;
        }
        if (ctx.paginated() && ctx.wrapWithRetry()) {
            return decode;
        }
        return buildPaginatedDecodeCase(ctx, decode);
    }

    private static ErlCase buildPaginatedDecodeCase(DispatchContext ctx, ErlExpr decodeCall) {
        PaginationInfo pi = BeamClientPaginationSupport.requirePaginationInfo(
                ctx.ctx().model(), ctx.ctx().service(), ctx.op());
        SymbolProvider sp = ctx.ctx().symbolProvider();
        StructureShape output = ctx.ctx().model().expectShape(ctx.op().getOutputShape(), StructureShape.class);
        StructureShape input = ctx.ctx().model().expectShape(ctx.op().getInputShape(), StructureShape.class);
        String inputRecord = recordName(sp.toSymbol(input));
        String inputToken = fieldName(sp, pi.getInputTokenMember());
        ErlExpr outputTokenExpr = buildRecordAccessExpr(
                "Output", output, pi.getOutputTokenMemberPath(), ctx.ctx().model(), sp);
        boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
        ErlExpr itemsExpr = hasItems
                ? buildRecordAccessExpr("Output", output, pi.getItemsMemberPath(), ctx.ctx().model(), sp)
                : null;

        return ErlCase.caseExpr(
                decodeCall,
                ErlClause.blockClause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("ok"),
                                ErlVarPattern.varPattern("Output"))),
                        ErlExprBlock.block(
                                buildAccumulationAndRecursion(
                                                ctx.opSym(),
                                                hasItems,
                                                itemsExpr,
                                                outputTokenExpr,
                                                inputRecord,
                                                inputToken)
                                        .toArray(ErlExpr[]::new))),
                ErlClause.blockClause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("error"),
                                ErlVarPattern.varPattern("Reason"))),
                        ErlTuple.tuple(ErlAtom.atom("error"), ErlVar.var("Reason"))));
    }

    private static List<ErlExpr> buildRetryWrappedBody(DispatchContext ctx, List<ErlExpr> core) {
        List<ErlExpr> body = new ArrayList<>();
        body.add(ErlMatch.match(
                ErlVarPattern.varPattern("RetryOpts"),
                ErlCall.call(
                        "maps",
                        "get",
                        ErlAtom.atom("retry"),
                        ErlVar.var("Config"),
                        ErlMap.map())));
        ErlangWriter funWriter = new ErlangWriter("fun.erl");
        funWriter.write("fun() ->");
        funWriter.indent();
        ErlangClientDispatchIr.writeExprs(funWriter, core);
        funWriter.dedent();
        funWriter.write("end");
        body.add(ErlCapturedBlock.capturedBlock(
                ctx.retryModule()
                        + ":with_retry("
                        + funWriter.toString().strip()
                        + ", RetryOpts)."));
        return body;
    }

    private static CaseTerminator caseTerminator(DispatchContext ctx) {
        if (ctx.mode() == DispatchBodyMode.SINGLE_PAGE) {
            return ctx.wrapWithRetry() ? CaseTerminator.NONE : CaseTerminator.STATEMENT;
        }
        if (!ctx.paginated() || !ctx.wrapWithRetry()) {
            return CaseTerminator.STATEMENT;
        }
        return CaseTerminator.NONE;
    }

    private static ErlExpr suffixCase(ErlCase caseExpr, CaseTerminator terminator) {
        if (terminator == CaseTerminator.NONE) {
            return caseExpr;
        }
        return ErlCapturedBlock.capturedBlock(caseExpr.asString() + terminator.suffix);
    }
}
