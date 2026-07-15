package io.smithy.beam.erlang;

import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.AtomPattern;
import io.beam.dsl.erlang.BlockExpr;
import io.beam.dsl.erlang.CaseExpr;
import io.beam.dsl.erlang.Clause;
import io.beam.dsl.erlang.Edoc;
import io.beam.dsl.erlang.Expression;
import io.beam.dsl.erlang.Fun;
import io.beam.dsl.erlang.FunClause;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.FunctionClause;
import io.beam.dsl.erlang.ListExpr;
import io.beam.dsl.erlang.LocalCallExpr;
import io.beam.dsl.erlang.Pattern;
import io.beam.dsl.erlang.Spec;
import io.beam.dsl.erlang.TupleExpr;
import io.beam.dsl.erlang.TuplePattern;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamErlangLayout;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ErlangClientPaginationDsl {
  private ErlangClientPaginationDsl() {}

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
                    List.of(VariablePattern.of("Config"), VariablePattern.of("Input")),
                    LocalCallExpr.of(
                        opName,
                        List.of(
                            Variable.of("Config"), Variable.of("Input"), ListExpr.of(List.of()))))),
            spec2,
            docOrNull);

    List<Expression> pageBody =
        ErlangClientDispatchDsl.operationBodyExprs(
            ctx,
            op,
            layout,
            wrapWithRetry,
            true,
            ErlangClientDispatchOperationDsl.DispatchBodyMode.PAGINATED_PAGE);

    FunctionClause arity3Clause =
        paginatedArity3Clause(ctx, service, op, layout, wrapWithRetry, pageBody, sp, opSym);

    Function arity3 = Function.of(opName, List.of(arity3Clause), spec3);

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
            VariablePattern.of("Config"), VariablePattern.of("Input"), VariablePattern.of("Acc"));
    Expression body =
        wrapWithRetry
            ? BlockExpr.commaSeparated(
                retryWrappedPageBody(ctx, service, op, layout, pageBody, sp, opSym), false)
            : (pageBody.size() == 1 ? pageBody.get(0) : BlockExpr.commaSeparated(pageBody, false));
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
    String inputRecord = ErlangClientDispatchOperationDsl.recordName(sp.toSymbol(input));
    String inputToken = ErlangClientDispatchOperationDsl.fieldName(sp, pi.getInputTokenMember());
    Expression outputTokenExpr =
        ErlangClientDispatchOperationDsl.buildRecordAccessExpr(
            "Output", output, pi.getOutputTokenMemberPath(), ctx.model(), sp);
    boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
    Expression itemsExpr =
        hasItems
            ? ErlangClientDispatchOperationDsl.buildRecordAccessExpr(
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
            ErlangClientDispatchOperationDsl.withRetryCall(layout.runtimeHttpModuleName(), pageFun),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("Output"))),
                    BlockExpr.commaSeparated(
                        ErlangClientDispatchOperationDsl.buildAccumulationAndRecursion(
                            opSym, hasItems, itemsExpr, outputTokenExpr, inputRecord, inputToken),
                        false)),
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                    TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("Reason"))))));
    return List.of(ErlangClientDispatchOperationDsl.retryOptsBinding(), retryCase);
  }
}
