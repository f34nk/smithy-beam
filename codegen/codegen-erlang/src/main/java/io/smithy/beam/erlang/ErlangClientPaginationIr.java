package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Edoc;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.Fun;
import io.beam.ir.erlang.FunClause;
import io.beam.ir.erlang.ListExpr;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.Pattern;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamErlangLayout;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ErlangClientPaginationIr {
  private ErlangClientPaginationIr() {}

  static List<Function> paginatedOperationFunctions(
      ErlangContext ctx,
      ServiceShape service,
      OperationShape op,
      BeamErlangLayout layout,
      boolean wrapWithRetry,
      String successReturnType,
      Edoc docOrNull) {
    SymbolProvider sp = ctx.symbolProvider();
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
    Symbol inSym = sp.toSymbol(input);
    String opName = opSym.getName();
    String specOutput = "{'ok', " + successReturnType + "} | {'error', term()}";
    Spec spec2 = Spec.of(opName + "(client_config(), " + inSym.getName() + ") -> " + specOutput);
    Spec spec3 =
        Spec.of(
            opName
                + "(client_config(), "
                + inSym.getName()
                + ", "
                + successReturnType
                + ") -> "
                + specOutput);

    Function arity2 =
        Function.of(
            opName,
            List.of(
                FunctionClause.of(
                    List.of(
                        VariablePattern.of("Config"), VariablePattern.of("Input")),
                    LocalCallExpr.of(
                        opName,
                        List.of(
                            Variable.of("Config"),
                            Variable.of("Input"),
                            ListExpr.of(List.of()))))),
            spec2,
            docOrNull,
            null);

    List<Expression> pageBody =
        ErlangClientDispatchIr.operationBodyExprs(
            ctx,
            op,
            layout,
            wrapWithRetry,
            true,
            ErlangClientDispatchOperationIr.DispatchBodyMode.PAGINATED_PAGE);

    FunctionClause arity3Clause =
        paginatedArity3Clause(
            ctx, service, op, layout, wrapWithRetry, pageBody, sp, opSym);

    Function arity3 = Function.of(opName, List.of(arity3Clause), spec3, null, null);

    return List.of(arity2, arity3);
  }

  private static FunctionClause paginatedArity3Clause(
      ErlangContext ctx,
      ServiceShape service,
      OperationShape op,
      BeamErlangLayout layout,
      boolean wrapWithRetry,
      List<Expression> pageBody,
      SymbolProvider sp,
      Symbol opSym) {
    List<Pattern> patterns =
        List.of(
            VariablePattern.of("Config"),
            VariablePattern.of("Input"),
            VariablePattern.of("Acc"));
    Expression body =
        wrapWithRetry
            ? BlockExpr.commaSeparated(
                retryWrappedPageBody(ctx, service, op, layout, pageBody, sp, opSym),
                false)
            : (pageBody.size() == 1
                ? pageBody.get(0)
                : BlockExpr.commaSeparated(pageBody, false));
    return FunctionClause.of(patterns, body);
  }

  private static List<Expression> retryWrappedPageBody(
      ErlangContext ctx,
      ServiceShape service,
      OperationShape op,
      BeamErlangLayout layout,
      List<Expression> pageBody,
      SymbolProvider sp,
      Symbol opSym) {
    PaginationInfo pi = BeamClientPaginationSupport.requirePaginationInfo(ctx.model(), service, op);
    StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
    StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
    String inputRecord = ErlangClientDispatchOperationIr.recordName(sp.toSymbol(input));
    String inputToken = ErlangClientDispatchOperationIr.fieldName(sp, pi.getInputTokenMember());
    Expression outputTokenExpr =
        ErlangClientDispatchOperationIr.buildRecordAccessExpr(
            "Output", output, pi.getOutputTokenMemberPath(), ctx.model(), sp);
    boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
    Expression itemsExpr =
        hasItems
            ? ErlangClientDispatchOperationIr.buildRecordAccessExpr(
                "Output", output, pi.getItemsMemberPath(), ctx.model(), sp)
            : null;

    Expression pageFun =
        Fun.of(
            List.of(
                FunClause.of(
                    List.of(),
                    pageBody.size() == 1
                        ? pageBody.get(0)
                        : BlockExpr.commaSeparated(pageBody, false))));
    Expression retryCase =
        CaseExpr.of(
            ErlangClientDispatchOperationIr.withRetryCall(
                layout.runtimeHttpModuleName(), pageFun),
            List.of(
                Clause.of(
                    TuplePattern.of(
                        List.of(AtomPattern.of("ok"), VariablePattern.of("Output"))),
                    BlockExpr.commaSeparated(
                        ErlangClientDispatchOperationIr.buildAccumulationAndRecursion(
                            opSym,
                            hasItems,
                            itemsExpr,
                            outputTokenExpr,
                            inputRecord,
                            inputToken),
                        false)),
                Clause.of(
                    TuplePattern.of(
                        List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                    TupleExpr.of(
                        List.of(AtomExpr.of("error"), Variable.of("Reason"))))));
    return List.of(ErlangClientDispatchOperationIr.retryOptsBinding(), retryCase);
  }
}
